/*
 Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.


 */

package edu.pvs.batchrunner

// import net.sf.json.JSONObject


import com.google.common.collect.Iterables
import edu.pvs.batchrunner.util.ListRangeIterable
import groovy.util.logging.Log
import com.google.gson.*

/**
 * User: Artur Andrzejak
 * Date: Aug 19, 2010, Time: 5:55:00 PM
 *
 * For configurating experiments (e.g. from JSON) using ExperimentResult
 */
@Typed @Log
class BatchConfigurator {
    // ExperimentResult er

    // for parsing Json
    private Gson gson = new Gson()
    private JsonParser parser = new JsonParser()
    private Map attribMap = [:]
    protected static Map iteratorLabelToType = ["const": 0, "list": 1, "range": 2]


    def ExperimentResult configureParamsFromJson(ExperimentResult newExperimentResult, String jsonInput, String batchName, JsonObject inputJsonObj = null) {
        ExperimentResult er
        if (newExperimentResult != null)
            er = newExperimentResult
        else
            er = new ExperimentResult(batchName)

        Map<String, ?> parameterSet = parseJsonSection2Map("ParamInfos", jsonInput, inputJsonObj)

        // loop over all parameters (elements of "ParamInfos")
        parameterSet.each { String name, JsonObject attributes ->
            // CAREFULL - the following is hard-coded according to the attributes of a parameter in JSON - NOT GOOD
            if (attributes.get("active").getAsInt() > 0) {
                // put all attributes to attibMap (as strings!)
                attribMap.clear()
                attribMap.name = name
                attribMap.fnAbr = attributes.get("fnAbr").getAsString()
                attribMap.init = attributes.get("init").getAsString()
                attribMap.doc = attributes.get("doc").getAsString()
                attribMap.condition = attributes.get("condition")?.getAsString()
                // decode flags into entries of attribMap
                def flags = attributes.get("flags")
                int[] flagsAsIntegers = gson.fromJson(flags, int[].class)
                // order is: isInput, putInSummary, saveValue, putInFileName
                attribMap.isInput = int2bool(flagsAsIntegers[0])
                attribMap.putInSummary = int2bool(flagsAsIntegers[1])
                attribMap.saveValue = int2bool(flagsAsIntegers[2])
                attribMap.putInFileName = int2bool(flagsAsIntegers[3])

                er.setEntryInfoFromMap(name, attribMap)
            }
        }
        return er
    }

    // returns: map of maps, each entry is <parameterName: loopMap>,
    // where loopMap has entries <loopName, ListRangeIterable>
    Map getLoopsFromJson(String jsonInput, JsonObject inputJsonObj = null) {

        Map<String, ?> parameterSet = parseJsonSection2Map("Loops", jsonInput, inputJsonObj)

        Map<String, Map> names2Loopsets = new LinkedHashMap<String, Map>()
        // loop over all parameters (elements of "ParamInfos")
        parameterSet.each { String name, JsonObject loopDefs ->

            Map loopset = new LinkedHashMap()
            names2Loopsets[name] = loopset

            // for each loop name, decode it into a ListRangeIterable
            Map<String, JsonElement> loopDefsMap = jsonObject2Map(loopDefs)
            loopDefsMap.each {String loopName, JsonElement loopDefinition ->
                ListRangeIterable iterable = parseIterable(loopDefinition)
                loopset[loopName] = iterable
                // debug
                // println ("Parsing (parName - loopName) : $name - $loopName")
            }
        }

        return names2Loopsets
    }

    BatchExecutor createBatchExecutor(String batchName, String jsonInput, JsonObject inputJsonObj = null) {
        // parse our json configuration

        // cannot use parseJsonSection2Map as we need the inputJsonObj later
        if (inputJsonObj == null)
            inputJsonObj = parser.parse(jsonInput)
        Map<String, ?> mapOfBatches = jsonObject2Map(inputJsonObj, "Batches")

        // extract the batch we need
        if (!mapOfBatches.containsKey(batchName)) {
            throw new IllegalArgumentException("Batch name '$batchName)' not found in the specified batches $mapOfBatches.keySet()")
        }
        //**************** parse and put into the BatchExecutor ************
        Map<String, JsonElement> batchData = jsonObject2Map((JsonObject) mapOfBatches.get(batchName))
        BatchExecutor be = new BatchExecutor()
        be.doc = batchData.get("doc").getAsString()

        // parse and instantiate filter (it is optional)
        be.optionalFilter = null
        if (batchData.containsKey("filterClass")) {
            def filterCode = batchData.get("filterClass").getAsString()
            if (filterCode.length() > 0)
                be.optionalFilter = (Filter) Class.forName(filterCode).newInstance();
        }

        // parse and instantiate the ExperimentCodeClass
        def experimentCodeClass = batchData.get("ExperimentCodeClass").getAsString()
        be.experimentCode = (ExperimentCode) Class.forName(experimentCodeClass).newInstance();

        // parse the settings map
        Map<String, JsonElement> settingsMapJson = jsonObject2Map((JsonObject) batchData["settings"])
        Map<String, Object> settingsMap = mapOfJsonPrimitives2mapOfObjects(settingsMapJson)
        be.settings = settingsMap

        // parse the iterables
        // return is: [listOfParamNames:listOfParamNames, listOfIterables:listOfIterables ]
        def combination = getLoopCombination((JsonObject) batchData.get("loopsCombination"), inputJsonObj)

        be.listOfParamNames = combination.listOfParamNames
        be.listOfIterables = combination.listOfIterables

        // finally, create the experimentResult and initialize from Json
        ExperimentResult er = configureParamsFromJson(null, null, batchName, inputJsonObj)

        // In order for the condition filter to work, the init value has to be set to a value which actually will be used in a combination
        // Thus, update the initvalues in er to have the first value of the iterable.
        [be.listOfParamNames, be.listOfIterables].transpose().collect { List it ->
            String paramName = it[0] as String
            Iterable iterable = it[1] as Iterable
            er.getEntryInfo(paramName).setInitValue(Iterables.get(iterable, 0))
        }

        be.experimentResult = er

        return be
    }

    //******************** Auxiliary Methods *******************************

    protected ListRangeIterable parseIterable(JsonElement loopDefinition) {
        int contentType // content type: 0: constant, 1: list, 2: range
        JsonElement content
        // println ("parse $loopDefinition")
        // first check the type
        if (loopDefinition.isJsonArray()) {
            content = loopDefinition
            contentType = 1
        } else if (loopDefinition.isJsonPrimitive()) {
            content = loopDefinition
            contentType = 0
        } else if (loopDefinition.isJsonObject()) {
            // no, we have form {"const" | "list" | "range" |  : JsonElement}
            JsonObject jsonObj = (JsonObject) loopDefinition
            for (String label: iteratorLabelToType.keySet()) {
                if (jsonObj.has(label)) {
                    contentType = iteratorLabelToType.get(label)
                    content = jsonObj.get(label)
                    break
                }
            }
        }
        // now turn JsonElement into a LRI object
        def result = new ListRangeIterable()
        switch (contentType) {
            case 0: // constant
                def jsonPrim = (JsonPrimitive) content
                def constValue
                if (jsonPrim.isNumber()) {
                    constValue = jsonPrim.getAsNumber()
                } else {
                    constValue = jsonPrim.getAsString()
                }
                result.fromConstant(constValue)
                break;
            case 1: // list
                // content must be JsonArray
                JsonArray jarray = (JsonArray) content
                List mylist = jarray.collect {
                    it.getAsString()
                }
                result.fromList(mylist)
                break
            case 2: // range
                // content must be JsonArray
                JsonArray jarray = (JsonArray) content
                Number start = jarray.get(0).getAsNumber()
                Number upto = jarray.get(1).getAsNumber()
                Number stepValue = 1
                if (jarray.size() > 2) stepValue = jarray.get(2).getAsNumber()
                result.fromRange(start, upto, stepValue)
                break
        }

        return result
    }

    protected Map<String, ?> jsonObject2Map(JsonObject inputJsonObj, String keyToSelect = null) {

        Map<String, ?> result = new LinkedHashMap<String, ?>()
        Set entries
        if (keyToSelect != null)
            entries = ((JsonObject) inputJsonObj.get(keyToSelect)).entrySet()
        else
            entries = inputJsonObj.entrySet()

        entries.each { Map.Entry<String, ?> entry ->
            // fetch parameter name and its attributes
            String name = entry.key
            def value = entry.value
            result.put(name, value)
        }
        return result
    }

    // Turns a map of JsonElements into a map of objects
    protected Map<String, Object> mapOfJsonPrimitives2mapOfObjects(Map<String, ?> inMap) {
        Map<String, ?> result = new LinkedHashMap<String, ?>(inMap.size())
        inMap.each { String key, val ->
            Object convertedElement = val
            if (val instanceof JsonElement) {
                JsonElement element = (JsonElement) val
                if (element.isJsonPrimitive()) {
                    def primitive = (JsonPrimitive) element
                    if (primitive.isNumber())
                        convertedElement = primitive.getAsNumber()
                    else
                        convertedElement = primitive.getAsString()
                }
            }
            result[key] = convertedElement
        }
        return result
    }

    protected boolean int2bool(Object inValue) {
        boolean res
        if (inValue == 0)
            res = false
        else if (inValue == 1)
            res = true
        else {
            throw new IllegalArgumentException("Arguments of parameter attribute 'flags' should be 0 or 1, found: $inValue")
        }
        return res
    }

    protected Map<String, ?> parseJsonSection2Map(String jsonSectionName, String jsonInput, JsonObject inputJsonObj) {
        if (inputJsonObj == null)
            inputJsonObj = parser.parse(jsonInput)
        Map<String, JsonElement> parameterSet = jsonObject2Map(inputJsonObj, jsonSectionName)
        return parameterSet
    }


    protected Map getLoopCombination(JsonObject iterablesInJson, JsonObject wholeJsonObject) {
        /* iterablesInJson is like:
         {   "traceIndex": "all",
             "targetColumnIndex": "all" }
        */

        // collect loops defintions by parsing the "Loops" specification
        Map mapOfLoops = getLoopsFromJson(null, wholeJsonObject)

        Map<String, JsonElement> combinationOfLoopsJson = jsonObject2Map(iterablesInJson)
        // the combinationOfLoops contains entries: paramName : iterable(loop)Name
        Map combinationOfLoops = mapOfJsonPrimitives2mapOfObjects(combinationOfLoopsJson)

        List listOfParamNames = []
        List listOfIterables = []
        // for each parameter in combinationOfLoops, find the specified loop (iterable)
        combinationOfLoops.each { String paramName, String loopName ->
            // verify that the current paramName has loop definitions, and get them
            if (!mapOfLoops.containsKey(paramName))
                throw new IllegalArgumentException("Parameter $paramName has no loop def's in the Json 'Loops' section but apprears in the loopsCombination: $iterablesInJson")
            Map suitableIterablesMap = mapOfLoops[paramName]

            def ListRangeIterable iterable
            if (!suitableIterablesMap.containsKey(loopName)) {
                //throw new IllegalArgumentException("No loop $loopName defined for parameter $paramName, but it is found in the loopsCombination: $iterablesInJson")
                log.info("No loop $loopName defined for parameter $paramName, using $loopName as literal value")
                def literal = ExperimentResult.convertToNumericIfPossible(loopName)
                iterable = new ListRangeIterable()
                iterable.fromConstant(literal)
            } else
                iterable = (suitableIterablesMap.get(loopName) as ListRangeIterable)

            listOfParamNames << paramName
            listOfIterables << iterable
        }

        return [listOfParamNames: listOfParamNames, listOfIterables: listOfIterables]
    }
}

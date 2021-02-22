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

import edu.pvs.batchrunner.util.Files
import edu.pvs.batchrunner.util.TimeDate

/**
 * Encapsulates parameters and results of a single experiment
 *
 * @author Artur Andrzejak
 * @version Aug 19, 2010, Time: 2:29:37 PM
 */
@Typed
class ExperimentResult extends LinkedHashMap {

    static class EntryInfo implements Serializable {
        String name
        String fileNameAbbrev
        String doc
        boolean isInput
        boolean putInSummary
        boolean saveValue
        boolean putInFileName
        Object initValue
        String condition
        boolean relevant

        void setInitValue(Object initValue) {
            this.initValue = convertToNumericIfPossible(initValue)
        }
    }

    // Configurable constants

    // Prefixes for the result and summary files
    static String resultPrefix = "res"
    static String resultPostfix = ".data"
    static String summaryPrefix = "summary-"
    static String summaryPostfix = ".csv"
    static String summarySeparator = ","

    // Paths
    static String defaultSavePath = "C:\\temp"
    static String defaultSummaryPath = "C:\\temp"

    // Running vars

    // Map holding (optional) meta-infos for each map Entry
    protected Map<String, EntryInfo> infos = new HashMap<String, EntryInfo>()
    protected String summaryFileName

    // constructor
    ExperimentResult(String batchName) {
        // create file name
        if (batchName != "") batchName += "-"
        summaryFileName = "${summaryPrefix}${batchName}${TimeDate.getCurrentDateTime(null)}$summaryPostfix"
    }

    @Override
    Object put(Object k, Object v) {
        return super.put(k, convertToNumericIfPossible(v))
    }



    def setEntryInfo(String name, String fileNameAbbrev = "", String doc = null,
                     boolean isInput = false, boolean putInSummary = false, boolean saveValue = false,
                     boolean putInFileName = false, Object initValue = null, String condition = null,
                     boolean relevant = true) {

        EntryInfo entry = infos.get(name, new EntryInfo())
        entry.name = name
        entry.doc = doc
        entry.isInput = isInput
        entry.putInSummary = putInSummary
        entry.saveValue = saveValue
        entry.putInFileName = putInFileName
        entry.fileNameAbbrev = fileNameAbbrev
        entry.initValue = initValue
        entry.condition = condition
        entry.relevant = relevant
        if (initValue != null) {
            this[name] = entry.initValue
        }
        // add to the infos (map of EntryInfos)
        infos[name] = entry
    }

    def resetValuesToInitValue() {
        for (String name in this.keySet()) {
            EntryInfo info = getEntryInfo(name)
            if (info?.initValue != null) {
                this[name] = info.initValue
            }
        }

    }

    static Object convertToNumericIfPossible(Object arg) {
        if (arg instanceof String) {
            String inStr = arg
            if (inStr.isNumber()) {
                if (inStr.isInteger())
                    return inStr.toInteger()
                else if (inStr.isDouble())
                    return inStr.toDouble()
            }
        }
        return arg
    }

    // sets entryinfos from a map (called from BatchConfigurator
    def setEntryInfoFromMap(String name, Map att) {
        // todo: check whether all map entries are present, if not, give meaningfull error message
        setEntryInfo(name,
                (String) att.fnAbr,
                (String) att.doc,
                (boolean) att.isInput,
                (boolean) att.putInSummary,
                (boolean) att.saveValue,
                (boolean) att.putInFileName,
                att.init,
                (String) att.condition
        )
    }

    EntryInfo getEntryInfo(String name) {
        return infos.get(name)
    }

    // Puts (certain) parameters into a new ExperimentResult object, and saves it to disk
    def saveToFile(String path) {

        path = (path == null) ? defaultSavePath : path

        ExperimentResult toSave = new ExperimentResult("")

        // find which entries should be saved, copy them
        for (String name in this.keySet()) {
            EntryInfo info = getEntryInfo(name)
            if (info?.saveValue) {
                toSave[(name)] = this[name]
            }
        }
        // generate file name
        StringBuilder fn = new StringBuilder(80)
        fn.append(resultPrefix)
        for (String name in this.keySet()) {
            EntryInfo info = getEntryInfo(name)
            if (info?.putInFileName) {
                // each entry has form ",(name)=(value), ..."
                fn.append(",").append(info.fileNameAbbrev).append("=")
                fn.append(this[name])
            }
        }
        fn.append(resultPostfix)

        Files.saveObjectToDisk(path, fn.toString(), toSave)
    }

    /**
     *
     Appends a summary line created from data specified in this container
     */
    def appendToSummary(String path) {
        path = (path == null) ? defaultSummaryPath : path

        def file = new File(path, summaryFileName)
        def fileExists = Files.fileExists(file)

        // file DNE, create and put header inside
        if (!fileExists) {
            def header = generateSummaryHeaderOrLine(true)
            Files.saveTextFile(file, header, false)
        }
        // now append the line
        def line = generateSummaryHeaderOrLine(false)
        Files.saveTextFile(file, line, true)
    }

    // Creates a header or a line of the summary file
    String generateSummaryHeaderOrLine(boolean generateHeader) {
        StringBuilder line = new StringBuilder(120)
        for (String name in this.keySet()) {
            EntryInfo info = getEntryInfo(name)
            if (info?.putInSummary) {
                def whatToOutput = generateHeader ? name : ((info?.relevant) ? this[name] : "")
                line.append(whatToOutput).append(summarySeparator)
            }
        }
        // now remove the last separator (assume that separator is only 1 char)
        line.deleteCharAt(line.length() - 1)
        line.append("\n")

        return line.toString()
    }

    void updateMaxAveFromList(String keyOfList) {
        List<Integer> valueList = (this[keyOfList] as List<Integer>)
        if (valueList != null && valueList.size() > 0) {
            def maxVal = valueList.max()
            def avgVal = (valueList.sum() as int) / valueList.size()
            // we assume that parameter names are <keyOfList>"Max" and <keyOfList>"Avg"
            this.put(keyOfList + "Max", maxVal)
            this.put(keyOfList + "Avg", avgVal)
        }
    }

    // Return parameter with name paramName as Integer (enforce conversion, otherwise error)
    Integer getInt(String paramName) {
        return Integer.parseInt((String)get(paramName))
    }

    // Return parameter with name paramName as Integer (enforce conversion, otherwise error)
    Double getDouble(String paramName) {
        return Double.parseDouble((String)get(paramName))
    }

    // Return parameter with name paramName as Integer (enforce conversion, otherwise error)
    Boolean getBool(String paramName) {
        return Boolean.parseBoolean((String)get(paramName))
    }

    // Return parameter with name paramName as String (enforce conversion, otherwise error)
    String getString(String paramName) {
        return get(paramName).toString()
    }

    // Return parameter with name paramName as a member of Enum class (enforce conversion, otherwise error)
    Enum getEnum(String paramName, Class enumeration) {
        return java.lang.Enum.valueOf(enumeration, get(paramName).toString())
    }


}

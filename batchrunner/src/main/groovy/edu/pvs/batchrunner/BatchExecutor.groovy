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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import edu.pvs.batchrunner.util.ListRangeIterable

/**
 * User: Artur Andrzejak
 * Date: Sep 13, 2010, Time: 1:44:39 PM
 * Executes a batch run (configured e.g. by BatchConfigurator
 */
@Typed
class BatchExecutor {
    // documentation
    String doc
    // class implementing single experiment code
    ExperimentCode experimentCode
    // optional filter for combinations
    Filter optionalFilter
    // additional map of settings to be passed to ExperimentCode upon start
    Map<String, Object> settings
    // list of parameter names corresponding to the listOfIterables
    List<String> listOfParamNames
    // list of iterables used to generate all param value combinations
    List<ListRangeIterable> listOfIterables
    // ExperimentResult object
    ExperimentResult experimentResult


    def startBatch(int maxNumberIterations = Integer.MAX_VALUE, int numberIterationsToSkip = 0) {
        // 1. Call the init method of experimentCode
        experimentCode.init(experimentResult, settings)

        // 2. Iterate
        // For each new parameter combination:
        //    check numberIterationsToSkip, numberIterationsToSkip
        //    update parameter values in experimentResult
        //    check filter
        //    if all ok, call experimentCode.run(experimentResult)
        //    todo: should experimentCode call ExperimentResult.appendToSummary and ExperimentResult.saveToFile or not?

        List<Set> listOfSets = []
        for (it in listOfIterables) {
            listOfSets << ImmutableSet.copyOf(it)
        }
        Iterable<List> gen = Sets.cartesianProduct(listOfSets)
        int iterationIndex = 0
        for (List combination in gen) {
            boolean skip
            skip = iterationIndex < numberIterationsToSkip
            skip |= iterationIndex >= maxNumberIterations
            iterationIndex++

            if (skip) continue
            experimentResult.resetValuesToInitValue()
            updateParametersInExperimentResult(combination)
            Filter conditionFilter = new ConditionFilter()
            int filterResult = conditionFilter.check(experimentResult)
            if (filterResult < 0) break
            if (filterResult == 0) continue

            if (optionalFilter != null) {
                filterResult = optionalFilter.check(experimentResult)
                if (filterResult < 0) break
                if (filterResult == 0) continue
            }
            // perform the experiment
            int runResult = experimentCode.run(experimentResult)

            if (runResult < 0) break
            if (runResult == 0) continue
        }

    }

    // Updates values in experimentResult, assuming that listOfParamNames names the parameter values in combination
    protected def updateParametersInExperimentResult(List combination) {
        int pos = 0
        for (String name in listOfParamNames) {
            experimentResult[name] = combination[pos]
            pos++
        }
    }

    static executeBatch(String JSONConfigurationAsString, String batchName, int maxNumberIterations = Integer.MAX_VALUE, int numberIterationsToSkip = 0) {
        BatchConfigurator batchConfigurator = new BatchConfigurator()
        BatchExecutor be
        be = batchConfigurator.createBatchExecutor(batchName, JSONConfigurationAsString)

        // Start batch processing
        be.startBatch(maxNumberIterations, numberIterationsToSkip)
    }

}

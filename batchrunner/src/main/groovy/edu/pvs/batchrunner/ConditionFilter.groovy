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

import groovy.util.logging.Log
import java.util.logging.Level

/**
 * Skips all experiments where the condition of the parameter is false and the parameter does not have the initial value
 */
@Typed @Log
class ConditionFilter implements Filter {
    @Override
    int check(ExperimentResult experimentResult) {
        for (String name in experimentResult.keySet()) {
            ExperimentResult.EntryInfo info = experimentResult.getEntryInfo(name)
            if (info != null) {
                info.relevant = true
                String condition = info.condition
                if (condition != null) {
                    boolean relevant
                    try {
                        relevant = (Eval.x(experimentResult, condition) as Boolean)
                    } catch (Exception e) {
                        relevant = true
                        log.log(Level.WARNING, "Relevancy condition \"${condition}\" could not be evaluated without errors", e)
                    }
                    if (!relevant) { // Condition is false
                        if (experimentResult[name] != info.initValue) { // Parameter has varied from initial value: Skip
                            return 0
                        } else { // Parameter is at initial value: Perform experiment, but mark parameter as irrelevant
                            info.relevant = false
                        }
                    }
                }
            }
        }
        return 1
    }
}

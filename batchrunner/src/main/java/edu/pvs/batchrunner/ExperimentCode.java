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

package edu.pvs.batchrunner;

import java.util.Map;

/**
 * Interface for execution of an experiment over a single combination of
 * parameters.
 *
 * @author Artur Andrzejak
 * @version Sep 13, 2010, Time: 1:48:51 PM
 */
public interface ExperimentCode {
    /**
     * Initialises this experiment to save its result in the given
     * ExperimentResult object and take the settings from the given map.
     * <p>
     * The settings in this map will be defined in the configuration json
     * file.
     * </p>
     *
     * @param newExperimentResult the object that will hold the result of
     *                            this experiment
     * @param settings            a map with settings, set in the json file.
     */
    void init(ExperimentResult newExperimentResult, Map<String, Object> settings);

    /**
     * Executes a single experiment.
     *
     * @param experimentResult the experiment result object that holds
     * @return an integer code for the result of this experiment:<br/>
     *         +1: all OK, collect results from ExperimentResult<br />
     *         0: discard this result but continue with next combination<br/>
     *         -1: discard this result and stop
     */
    int run(ExperimentResult experimentResult);
}

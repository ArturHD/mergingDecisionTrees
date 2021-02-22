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
 * User: Artur Andrzejak
 * Date: Sep 13, 2010, Time: 4:50:47 PM
 * User-defined class for executing a single experiment under batchrunner.
 * Method run() is called for every new input parameter combination.
 */
public class ExperimentCodeTest implements ExperimentCode {

    // This method is called only once, after this class is instantiated
    @Override
    public void init(ExperimentResult newExperimentResult, Map settings) {
        System.out.println("\n ExperimentCodeTest.init() called");
    }


    // This method is called for every new combination of input parameter values
    @Override
    public int run(ExperimentResult experimentResult) {
        System.out.println("\n*** New experiment with params = " + experimentResult);

        // ===== Getting parameters ======

        // Getting an input parameter value
        Object traceIndexAsObj = experimentResult.get("traceIndex");
        int traceIndex = Integer.parseInt((String) traceIndexAsObj.toString());
        System.out.println("\nGot traceIndex = " + traceIndex);

        // Getting an input parameter value with type cast - simpler
        traceIndex = experimentResult.getInt("traceIndex");


        // =============================
        // Execute your experiment here
        // =============================


        // ===== Saving results in experimentResult =====

        // Put some output result
        double someOutput = 1.111;
        experimentResult.put("Output_pctCorrect", someOutput);


        // ===== Writing results =====

        // Typical output - append a line to the summary file
        String resultsDirectory = "../results";
        experimentResult.appendToSummary(resultsDirectory);

        // optional - serialize and save experimentResult - usually not used
        // experimentResult.saveToFile(resultsDirectory);


        // Return 1 to continue with the next iteration, or 0 to terminate
        return 1;
    }
}

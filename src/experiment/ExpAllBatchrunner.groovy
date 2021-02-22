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

package experiment

import edu.pvs.batchrunner.BatchConfigurator
import edu.pvs.batchrunner.BatchExecutor
import edu.pvs.batchrunner.util.Files
import edu.pvs.batchrunner.util.TimeDate
import java.util.logging.FileHandler
import java.util.logging.Logger

/**
 * User: Artur Andrzejak
 * 05.02.2012
 * This is the script which starts all the batch experiments (when using batchrunner)
 */


// Instantiate batch configurator
BatchConfigurator configurator = new BatchConfigurator()
// read in file -  in idea set working dir to "/trees"
def fileName = "src/config/expAll-01.json"
def jsonString = Files.readFile(fileName)
if (!jsonString)
    throw new FileNotFoundException("Filename: $fileName")

//def batchName = "testBatch"
//def batchName = "fastTestA"
//def batchName = "testBatch"
//def batchName = "conceptDrift"
//def batchName = "novel"
def batchName = "onePass"

// optionally, get config file name from command line
if (args.size() > 0) {
    batchName = args[0]
}

println "==== Batchrunner: using batch '$batchName' =====\n"

// Run the experiments
BatchExecutor be = configurator.createBatchExecutor(batchName, jsonString)
def fh = new FileHandler("results/${TimeDate.getCurrentDateTime(null)}.log")
Logger.getLogger("").addHandler(fh)
be.startBatch()
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

@Typed package edu.pvs.batchrunner

import edu.pvs.batchrunner.util.Files

/**
 * User: Artur Andrzejak
 * Date: Aug 19, 2010, Time: 2:55:49 PM
 * Example of a start of batchrunner execution
 * CAUTION: As the "testJsonConfig.json" is in batchrunner\src\test\resources, the working directory
 *          must point to this dir at start - otherwise "File not found exception" occurs
 */

// Read in file JSON configuration into a String
String JSONConfigurationName = "testJsonConfig.json"
String batchName = "basicBatch"

String configAsString = Files.readFile(JSONConfigurationName)
if (!configAsString) {
    throw new FileNotFoundException("Did not find file named testJsonConfig.json")
}

// Start batch processing
// Optional parameters: executeBatch (*, *, int maxNumberIterations, int numberIterationsToSkip)
BatchExecutor.executeBatch(configAsString, batchName)

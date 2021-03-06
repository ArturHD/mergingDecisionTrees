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

package WekaGroovyExamples

import weka.classifiers.Evaluation
import weka.classifiers.trees.J48
import weka.core.converters.ConverterUtils.DataSource
import weka.core.Instances
import weka.core.Range

/** 
 * An example of using J48 from within Groovy.
 * <p/>
 * First parameter is the dataset to be processed by J48. The last attribute
 * is used as class attribute.
 *
 * @author: FracPete (fracpete at waikato dot ac dot nz)
 */

if (args.size() == 0) {
  println "Usage: UsingJ48Ext.groovy <ARFF-file>"
  System.exit(0)
}

// load data and set class index
print "Loading data..."
data = DataSource.read(args[0])
data.setClassIndex(data.numAttributes() - 1)

// create the model
evaluation = new Evaluation(data)
buffer = new StringBuffer()  // buffer for predictions
attRange = new Range()  // no additional attributes to output
outputDistribution = false  // we don't want distribution
j48 = new J48()
j48.buildClassifier(data)
evaluation.evaluateModel(j48, data, buffer, attRange, outputDistribution)

// print out the built model
println "--> Generated model:\n"
println j48

println "--> Evaluation:\n"
println evaluation.toSummaryString()

println "--> Predictions:\n"
println buffer


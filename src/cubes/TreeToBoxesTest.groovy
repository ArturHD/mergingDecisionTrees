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

package cubes

import weka.classifiers.Evaluation
import weka.classifiers.trees.J48
import weka.classifiers.trees.j48.J48toCubes
import weka.core.converters.ConverterUtils.DataSource
import weka.filters.Filter
import weka.filters.unsupervised.attribute.Discretize

/**
 * An example of using J48 from within Groovy.
 * <p/>
 * First parameter is the dataset to be processed by J48. The last attribute
 * is used as class attribute.
 *
 * @author: FracPete (fracpete at waikato dot ac dot nz)
 * + AA (15.01.2012) - extracting paths from J48 tree
 * AA: tested with iris.arff and segment-challenge.arff
 */

if (args.size() == 0) {
    println "Usage: UsingJ48.groovy <ARFF-file>"
    System.exit(0)
}

// load data and set class index
data = DataSource.read(args[0])
data.setClassIndex(data.numAttributes() - 1)

// discretize
def filter = new Discretize()
filter.setInputFormat(data)
filter.setBins(3)
data = Filter.useFilter(data, filter)

// create the model
rt = new J48()
rt.buildClassifier(data)

// print out the built model
println rt

rttb = new J48toCubes()
rttb.getReverseDAG(rt.getRoot())
rttb.leaves.each {
    rttb.visitPathFast(it)
    println rttb.leafToCube(it)
}

def cubes = J48toCubes.treeToBoxes(rt)

println cubes

def evaluation = new Evaluation(data)
evaluation.crossValidateModel(rt, data, 10, new Random(1))
println evaluation.toSummaryString()
println evaluation.toClassDetailsString()
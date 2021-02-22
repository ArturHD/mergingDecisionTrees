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
// A little Groovy script that evaluates several classifiers on multiple train/test pairs.
//
// Author: FracPete (fracpete at waikato dot ac dot nz)

import weka.classifiers.Evaluation
import weka.classifiers.Classifier
import weka.core.converters.ConverterUtils.DataSource
import weka.core.Utils

// list of training/test sets
training_dir  = "/directory/containing/training/sets"
training_sets = ["train1.arff", "train2.arff"]
test_dir      = "/directory/containing/test/sets"
test_sets     = ["test1.arff", "test2.arff"]
assert training_sets.size() == test_sets.size()

// list of classifiers
classifiers = ["weka.classifiers.trees.J48 -C 0.25", "weka.classifiers.trees.J48 -U", "weka.classifiers.functions.SMO -K \"weka.classifiers.functions.supportVector.PolyKernel -E 2\""]

// perform the evaluation
for (i in 0..(training_sets.size()-1)) {
  for (c in classifiers) {
    // progress info
    println "\n" + training_sets[i] + "/" + test_sets[i] + "/" + c

    // load datasets
    train = DataSource.read(training_dir + "/" + training_sets[i])
    if (train.classIndex() == -1) train.setClassIndex(train.numAttributes() - 1)
    test  = DataSource.read(test_dir + "/" + test_sets[i])
    if (test.classIndex() == -1) test.setClassIndex(test.numAttributes() - 1)
    // make sure they're compatible
    assert train.equalHeaders(test)

    // instantiate classifier
    options    = Utils.splitOptions(c)
    classname  = options[0]
    options[0] = ""
    cls        = Classifier.forName(classname, options)

    // build and evaluate classifier
    cls.buildClassifier(train)
    eval = new Evaluation(train)
    eval.evaluateModel(cls, test)

    // output statistics, e.g., Accuracy
    println "  Accuracy: " + eval.pctCorrect()
  }
}
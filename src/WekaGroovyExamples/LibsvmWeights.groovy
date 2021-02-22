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

import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibSVM
import weka.core.converters.ConverterUtils.DataSource

import java.util.Random

/** 
 * Determines the best class-weights for LibSVM from a list of weight strings. 
 * <p/>
 * Just runs LibSVM on a binary dataset and chooses the class-weights
 * with the best accuracy.
 * <p/>
 * Note: The Groovy and libsvm classes must be present in the classpath to run 
 *       this script.
 *
 * @author FracPete (fracpete at waikato dot ac dot nz)
 */

// dataset provided?
if (args.size() == 0) {
  println "Usage: LibsvmWeights.groovy <ARFF-file>"
  return;
}

// load data
data = DataSource.read(args[0])
if (data.classIndex() == -1) data.setClassIndex(data.numAttributes() - 1)
if (data.classAttribute().numValues() != 2) {
  println "Dataset needs a binary class!"
  return;
}

// evaluate weights
def weights = ["1.0 1.0", "1.0 0.7", "1.0 0.4"]
def evals = []
for (weight in weights) {
  println "\n\n--> Processing weights: " + weight
  cls = new LibSVM();
  cls.setWeights(weight)
  eval = new Evaluation(data);
  eval.crossValidateModel(cls, data, 10, new Random(1));
  evals.add(eval)
}

// find best accuracy
index = 0
best  = evals[index].pctCorrect()
for (i = 1; i < weights.size(); i++) {
  if (evals[i].pctCorrect() > best) {
    index = 0
    best  = evals[i].pctCorrect()
  }
}
println "\n\nBest accuracy was found for weights: " + weights[index]

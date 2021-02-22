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

import weka.classifiers.Classifier
import weka.classifiers.Evaluation
import weka.core.converters.ConverterUtils.DataSource
import weka.core.Utils
import weka.filters.Filter
import weka.filters.unsupervised.instance.RemoveWithValues

/*
  This groovy example class takes a dataset and a classifier and performs
  a "custom" cross-validation. The folds for this cross-validation are
  determined by the values of a specified (nominal) attribute. E.g., if
  the attribute specified with -C has the labels "1,2,3,4,5", then this
  cross-validation will have 5 folds. For each train/test pair, the test
  data will contain the instances with the given value, whereas the 
  train set won't. In our example, the train set will have all the instances
  that have the values 2,3,4,5 in the specified attribute, and the test
  set only with  value 1. In fold 2, train set is based on 1,3,4,5 and
  test set on 2. And so forth...
  At the end, the evaluation is printed to stdout.

  Usage (from commandline):
    groovy -classpath weka.jar
           CustomCV.groovy 
           -t <dataset> 
           -W <classifier classname> 
           -C <attribute index for folds, default: first> 
           -c <class index, default: last>
           -- <additional classifier options>

  Note:
  attribute indices are starting from "1"; "first" and "last" are accepted
  as well.

  For more information on using Weka from Groovy, see:
    http://weka.wiki.sourceforge.net/Using+Weka+from+Groovy

  Author: FracPete (fracpete at waikato dot ac dot nz)
*/

////////////////////
// get parameters //
////////////////////

// 1. data
tmp = Utils.getOption('t', args)
if (tmp == '') throw new Exception('No dataset provided!')
dataset = DataSource.read(tmp)

// 2. class index
tmp = Utils.getOption('c', args)
if (tmp.length() == 0) tmp = 'last'
if (tmp == 'first') cindex = 0
else if (tmp == 'last') cindex = dataset.numAttributes() - 1
else cindex = Integer.parseInt(tmp) - 1
dataset.setClassIndex(cindex)

// 3. attribute index
tmp = Utils.getOption('C', args)
if (tmp.length() == 0) tmp = 'last'
if (tmp == 'first') aindex = 0
else if (tmp == 'last') aindex = dataset.numAttributes() - 1
else aindex = Integer.parseInt(tmp) - 1

// 4. classifier
tmp = Utils.getOption('W', args)
if (tmp == '') throw new Exception('No classifier provided!')
classifier = Classifier.forName(tmp, Utils.partitionOptions(args))

////////////////////////
// perform evaluation //
////////////////////////

eval = new Evaluation(dataset)
folds = dataset.attribute(aindex).numValues()
println "Dataset: " + dataset.relationName()
println "Class: " + dataset.classAttribute().name() + "/" + (cindex+1)
println "Fold attribute: " + dataset.attribute(aindex).name() + "/" + (aindex+1)
println "Folds: " + folds
print "Processing"
for (i = 0; i < folds; i++) {
  print "."
  // setup filters
  filterTrain = new RemoveWithValues()
  filterTrain.setAttributeIndex("" + (aindex+1))
  filterTrain.setNominalIndices("" + (i+1))
  filterTrain.setInputFormat(dataset)
  filterTest = new RemoveWithValues()
  filterTest.setAttributeIndex("" + (aindex+1))
  filterTest.setNominalIndices("" + (i+1))
  filterTest.setInvertSelection(true)
  filterTest.setInputFormat(dataset)

  // generate data
  train = Filter.useFilter(dataset, filterTrain)
  test  = Filter.useFilter(dataset, filterTest)

  // train + evaluate classifier
  cls = Classifier.makeCopy(classifier)
  cls.buildClassifier(train)
  eval.evaluateModel(cls, test)
}
println ""

///////////////////////
// output evaluation //
///////////////////////

print eval.toSummaryString()


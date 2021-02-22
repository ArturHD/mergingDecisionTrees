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

package classifiers

import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import experiment.Tools
import groovy.util.logging.Log
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances

import static experiment.Tools.*

/**
 * User: flangner
 * Date: 11.07.12
 * Time: 17:37
 *
 * This Classifier summarizes the original data to enable a one-pass classification. The raw data will be read only once
 * during the whole classification process.
 */
@Typed @Log
class RandomSamplingClassifier extends Classifier implements PerfUtils {

    private Classifier classifier = null

    /**
     * Builds a classifier trained on merged DatasetSummaries of k fragments of (training) data
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data training data
     */
    @Override
    void buildClassifier(Instances data) {

        // parameters that might be found at ExperimentResult
        // if not, they are initialized with default values
        final ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
        final int k = (results) ? results.getInt("Ek") : 1
        final String type = (results && results.Etf) ? results.getString("Etf") : "J48"

        // If Ncentro is <= 1.0 then it is the fraction of the # of training instances
        //    else: Ncentro is the absolute number of centroids
        final double nCentroidsAsDouble = (results) ? results.getDouble("Ncentro") : 1.0

        // prepare the data and build the classifier
        final List<Instances> splits = createKSplits(k, data, results)
        final Instances summary = summarize(splits, k, nCentroidsAsDouble)
        classifier = buildClassifier(summary, type)
    }

    /**
     * Predicts the class memberships for a given instance. If
     * an instance is unclassified, the returned array elements
     * must be all zero. If the class is numeric, the array
     * must consist of only one element, which contains the
     * predicted value. Note that a classifier MUST implement
     * either this or classifyInstance().
     *
     * @param sample the instance to be classified
     * @return an array containing the estimated membership
     * probabilities of the test instance in each class
     * or the numeric prediction
     * @exception Exception if distribution could not be
     * computed successfully
     */
    @Override
    public double[] distributionForInstance(Instance sample) throws Exception {

        if (classifier != null) {

            return classifier.distributionForInstance(sample)
        } else {

            throw new UnsupportedOperationException(
                    "The one pass classifier has not been build jet. Use buildClassifier() to build the classifier.")
        }
    }

    /**
     * Summarizes the input and prepares it for classification.
     *
     * @param compression
     * @param k - the number of splits.
     * @param splits - the split data. May be null if data should be loaded from arff files.
     * @param mergeStrategy - how to merge auxiliary universes of the splits.
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return a limited amount of instances summarizing the input.
     *
     * @throws Exception
     */
    private Instances summarize(List<Instances> splits, int k, double compression) throws Exception {

        // summarize the splits s(d_i)
        Instances[] summaries = new Instances[k]
        double keep = (1.0 - compression) * 100.0
        for (int i = 0; i < k; i++) {

            final ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
            final Instances split = (splits) ? splits.get(i) : loadArff(results.getString("split${i}"))
            summaries[i] = Tools.removePercentageOfInstances(split, keep)
        }

        for (int i = 1; i < summaries.length; i++) {
            for (int j = 0; j < summaries[i].numInstances(); j++) {
                summaries[0].add(summaries[i].instance(j))
            }
        }

        return summaries[0]
    }

    /**
     * Builds a classifier for the trainingData using the given classifier type.
     *
     * @param trainingData
     * @param classifierName - of the classifier to use.
     * @return the classifier initialized for classification.
     *
     * @throws Exception
     */
    private Classifier buildClassifier(Instances trainingData, String classifierName) throws Exception {

        Classifier result = getClassifierInstanceFromClassName(classifierName)
        result.buildClassifier(trainingData)

        return result
    }
}

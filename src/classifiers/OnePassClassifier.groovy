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

import classifiers.onepass.DatasetSummary

import classifiers.onepass.DataSummarizer
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances

import static experiment.Tools.createKSplits
import static experiment.Tools.loadArff
import classifiers.onepass.DatasetSummaryMerger

import static experiment.Tools.getClassifierInstanceFromClassName
import groovy.util.logging.Log

/**
 * User: flangner
 * Date: 11.07.12
 * Time: 17:37
 *
 * This Classifier summarizes the original data to enable a one-pass classification. The raw data will be read only once
 * during the whole classification process.
 */
@Typed @Log
class OnePassClassifier extends Classifier implements PerfUtils {

    private Classifier classifier = null

    /**
     * Builds a classifier trained on merged DatasetSummaries of k fragments of (training) data
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data training data
     */
    @Override
    void buildClassifier(Instances data) {

        int numInstances = data.numInstances()

        // parameters that might be found at ExperimentResult
        // if not, they are initialized with default values
        final ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
        final int k = (results) ? results.getInt("Ek") : 1
        final String type = (results && results.Etf) ? results.getString("Etf") : "J48"
        final String mergeStrategy = (results) ? results.getString("MS") : "Linear"
        final String summarizeStrategy = (results) ? results.getString("SumStra") : "Distance"

        // If Ncentro is <= 1.0 then it is the fraction of the # of training instances
        //    else: Ncentro is the absolute number of centroids
        final int numCentroids
        final int numCentroidsProSplit
        final double nCentroidsAsDouble = (results) ? results.getDouble("Ncentro") : 1000

        if (nCentroidsAsDouble <= 1.0) {

            numCentroids = Math.floor(numInstances * nCentroidsAsDouble)
            numCentroidsProSplit = numCentroids
            //TODO numCentroidsProSplit = Math.floor((numInstances / k) * nCentroidsAsDouble) ?
            // which strategy to choose depends on the usage scenario

        } else {

            numCentroids = (int) nCentroidsAsDouble
            numCentroidsProSplit = numCentroids
        }

        // prepare the data and build the classifier
        final List<Instances> splits = createKSplits(k, data, results)
        final Instances summary = summarize(splits, k, numCentroidsProSplit, numCentroids, mergeStrategy,
                                            summarizeStrategy)
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
     * @param Nsplit - the size of auxiliary clusters per split in number of centroids.
     * @param Nfinal - the final amount of centroids per cluster.
     * @param k - the number of splits.
     * @param splits - the split data. May be null if data should be loaded from arff files.
     * @param mergeStrategy - how to merge auxiliary universes of the splits.
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return a limited amount of instances summarizing the input.
     *
     * @throws Exception
     */
    private Instances summarize(List<Instances> splits, int k, int Nsplit, int Nfinal, String mergeStrategy,
                                String summarizeStrategy) throws Exception {

        // summarize the splits s(d_i)
        DatasetSummary[] summaries = new DatasetSummary[k]
        for (int i = 0; i < k; i++) {

            final Instances split = (splits) ? splits.get(i) : loadArff("split${i}")
            summaries[i] = DataSummarizer.summarize(Nsplit, split, summarizeStrategy)
            log.info("Split $i had an average number of recursion pro update of ${summaries[i].getAverageRecursionStepsProUpdate()}.")
        }

        // join the summaries
        final DatasetSummary auxCluster
        switch (mergeStrategy) {

            case "Linear":
                auxCluster = DatasetSummaryMerger.linearMerge(summaries, Nfinal, summarizeStrategy)
                break

            case "DAC":
                auxCluster = DatasetSummaryMerger.divideAndConquerMerge(summaries, Nfinal, summarizeStrategy)
                break

            default:
                throw new Exception("Unknown classification mechanism requested.")
                break
        }

        log.info("The merge result had an average number of recursions pro update of ${auxCluster.getAverageRecursionStepsProUpdate()}.")

        return auxCluster.getInstances()
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

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

package classifiers.onepass

import weka.core.Instances

/**
 * User: flangner
 * Date: 12.07.12
 * Time: 11:16
 *
 * Mechanics to summarize a labeled data set to an bunch of auxiliary clusters within a cluster universe with a settled
 * maximum amount of centroids.
 */
@Typed
class DataSummarizer {

    /**
     *
     * @param N - the maximal amount of auxiliary centroids of all clusters of a DatasetSummary.
     * @param data - to summarize.
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return the DatasetSummary representing a summary of the input data.
     */
    static DatasetSummary summarize(int N, Instances data, String summarizeStrategy) {

        return new DatasetSummary(N, relativeClassFrequencies(data), data, summarizeStrategy)
    }

    /**
     * @param data
     * @return the normalized shares for each class available in the given data set, determined by there relative
     *         occurrences.
     */
    private static Map<Double, Double> relativeClassFrequencies(Instances data) {

        final Map result = new HashMap<Double, Double>()

        for (int i = 0; i < data.numInstances(); i++){

            final double cv = data.instance(i).classValue()
            double oldValue = result.get(cv, 1.0)   // get old value or 1.0 if not yet in map
            result.put(cv, (oldValue + 1.0))
        }

        // Normalize by diving by total # of instances
        final double totalInstances = data.numInstances()
        for (double classValue in result.keySet()) {
            result.put(classValue, result.get(classValue) / totalInstances)
        }

        return result
    }
}

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

/**
 * User: flangner
 * Date: 12.07.12
 * Time: 12:42
 */
@Typed
class DatasetSummaryMerger {

    /**
     * Merges a list of DatasetSummaries by  hierarchical (divideAndConquer)  method.
     * This algorithm will only work with an amount of clusters that is a power of two.
     * The given DatasetSummaries will be changed by this algorithm.
     *
     * @param clusters
     * @param N
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return a summary of the given DatasetSummaries
     */
    static DatasetSummary divideAndConquerMerge(DatasetSummary[] clusters, int N, String summarizeStrategy) {

        assert (Math.log(clusters.length) / Math.log(2)) % 1 == 0.0 : """The given amount of clusters
                                                cannot be merged properly, because it's not a power of two."""

        if (clusters.length >= 2) {
            recursiveDAndCMerge(0, (clusters.length - 1), clusters, N, summarizeStrategy)
        }

        return clusters[0]
    }

    /**
     * Merges a list of DatasetSummaries linearly from the left to the right.
     *
     * @param clusters
     * @param N
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return a summary of the given DatasetSummaries
     */
    static DatasetSummary linearMerge(DatasetSummary[] clusters, int N, String summarizeStrategy) {

        DatasetSummary result;
        if (clusters.length >= 2) {

            assert clusters[0] != null
            assert clusters[1] != null
            result = merge(clusters[0], clusters[1],  N, summarizeStrategy)
            for (int i = 2; i < clusters.length; i++) {
                result = merge(result, clusters[i], N, summarizeStrategy)
            }
        } else {
            result = clusters[0]
        }

        return result
    }

    /**
     * Merges the two given DatasetSummaries with each other considering a limited amount of overall
     * centroids N.
     *
     * @param u1
     * @param u2
     * @param N
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return the common auxiliary cluster universes.
     */
    private static DatasetSummary merge(DatasetSummary u1, DatasetSummary u2, int N, String summarizeStrategy) {

        // build common key set, because we want all data to be processed
        Set<Double> allClasses = new HashSet<Double>()
        allClasses.addAll(u1.keySet())
        allClasses.addAll(u2.keySet())

        final Map<Double, Double> relativeFrequencies = averageRelativeClassFrequencies(u1, u2, allClasses)

        final DatasetSummary result = u1.clone(relativeFrequencies, u1.getWeight() + u2.getWeight())

        for (Double key : allClasses) {

            final int numCentroids = Math.floor(relativeFrequencies.get(key) * N)
            result.put(key, merge(u1.get(key), u2.get(key), (numCentroids == 0) ? 1 : numCentroids, summarizeStrategy))
        }

        return result
    }

    /**
     * Merges the given DatasetSummaries considering the given maxNumCentroids.
     *
     * @param c1
     * @param c2
     * @param maxNumCentroids
     * @param summarizeStrategy - how to summarize samples.
     *
     * @return a DatasetSummaries containing all information of c1 and c2 with maxNumCentroids.
     */
    private static CentroidsForOneClass merge (CentroidsForOneClass c1, CentroidsForOneClass c2, int maxNumCentroids,
                                               String summarizeStrategy) {

        final CentroidsForOneClass result = new CentroidsForOneClass(maxNumCentroids)

        // if one of c1, c2 is null or empty, return the other one
        if (c1 == null || c1.size() == 0)
            return c2
        if (c2 == null || c2.size() == 0)
            return c1

        Iterator<AuxiliaryCentroid> iter1 = c1.iterator()
        Iterator<AuxiliaryCentroid> iter2 = c2.iterator()

        while (iter1.hasNext() || iter2.hasNext()) {

            if (iter1.hasNext()) {
                result.addCentroid(iter1.next(), summarizeStrategy)
            }

            if (iter2.hasNext()) {
                result.addCentroid(iter2.next(), summarizeStrategy)
            }
        }

        return result
    }

    /**
     * Calculates the weighted average relative frequencies of classes for two given DatasetSummaries.
     * The weight of each DatasetSummary will be considered.
     *
     * @param u1
     * @param u2
     * @param classIndices
     * @return the relative frequencies for each class value covered by the universes u1 and u2.
     */
    private static Map<Double, Double> averageRelativeClassFrequencies(DatasetSummary u1,
                                                                  DatasetSummary u2,
                                                                  Set<Double> classIndices) {

        assert u1 != null && u2 != null

        final Map<Double, Double> result = new HashMap<Double, Double>()

        final int commonWeight = u1.getWeight() + u2.getWeight()
        final double u1Share = u1.getWeight() / (double) commonWeight
        final double u2Share = 1.0 - u1Share

        for (Double key : classIndices) {

            result.put(key,
                 ((u1.getRelativeFrequencies().containsKey(key)) ? u1.getRelativeFrequencies().get(key) * u1Share : 0) +
                 ((u2.getRelativeFrequencies().containsKey(key)) ? u2.getRelativeFrequencies().get(key) * u2Share : 0))
        }

        return result
    }

    /**
     * Recursive algorithm for the divide and conquer merging procedure.
     *
     * @param start
     * @param end
     * @param datasetSummaries
     * @param summarizeStrategy - how to summarize samples.
     */
    private static void recursiveDAndCMerge(int start, int end, DatasetSummary[] datasetSummaries, int N,
                                            String summarizeStrategy) {

        final int lower_pivot = Math.floor((end + start) / 2.0)
        final int upper_pivot = Math.ceil((end + start) / 2.0)

        // there is a real pivot element -> we need to divide further
        if (start < lower_pivot && upper_pivot < end) {

            recursiveDAndCMerge(start, lower_pivot, datasetSummaries, N, summarizeStrategy)
            recursiveDAndCMerge(upper_pivot, end, datasetSummaries, N, summarizeStrategy)
            datasetSummaries[start] = merge(datasetSummaries[start], datasetSummaries[upper_pivot], N,
                                            summarizeStrategy)

        // now it is time to conquer
        } else {

            datasetSummaries[start] = merge(datasetSummaries[start], datasetSummaries[end], N, summarizeStrategy)
        }
    }
}

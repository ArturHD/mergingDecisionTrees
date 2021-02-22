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

import weka.core.Instance
import weka.core.Instances

/**
 * User: flangner
 * Date: 12.07.12
 * Time: 12:36
 *
 * Data type to maintain a summarized representation of a labeled data set.
 * It extends HashMap: classes are keys, and corresponding value is a centroid-container for this class
 */
@Typed
class DatasetSummary extends HashMap<Double, CentroidsForOneClass> {

    private final Instances metadata
    private final Map<Double, Double> relativeClassFrequencies

    // the total amount of samples in this DatasetSummary
    private final int weight

    /**
     * Default constructor. Initializes an auxiliary cluster for each given frequency. One frequency is defined for
     * each class label, available in the data set. Also the data will be loaded by the constructor.
     *
     * @param N
     * @param relativeClassFrequencies
     * @param data
     * @param summarizeStrategy - how to summarize samples.
     */
    DatasetSummary(int N, Map<Double, Double> relativeClassFrequencies, Instances data, String summarizeStrategy) {

        this.relativeClassFrequencies = relativeClassFrequencies
        for (Map.Entry<Double, Double> entry : relativeClassFrequencies.entrySet()) {

            final int numCentroids = Math.floor(entry.value * N)
            put(entry.key, new CentroidsForOneClass((numCentroids == 0) ? 1 : numCentroids))
        }

        this.metadata = new Instances(data, N)
        this.weight = data.numInstances()
        // Scan instances and add each one to the appropriate clusterGroup
        for (int i = 0; i < weight; i++){

            get(data.instance(i).classValue()).addSample(data.instance(i), summarizeStrategy)
        }
    }

    private DatasetSummary(Map<Double, Double> relativeClassFrequencies, Instances data, int weight) {

        this.relativeClassFrequencies = relativeClassFrequencies
        this.metadata = data
        this.weight = weight
    }

    /**
     * @return the relative frequencies of samples covered by this data representation.
     */
    Map<Double, Double> getRelativeFrequencies() {

        return relativeClassFrequencies
    }

    /**
     * @return the weight of this universe given by the amount of samples covered.
     */
    int getWeight() {

        return weight
    }

    /**
     * @return an instances representation of the auxiliary cluster.
     */
    Instances getInstances() {

        final Instances result = new Instances(metadata)
        for (CentroidsForOneClass cluster : values()) {

            for (AuxiliaryCentroid centroid : cluster) {

                result.add(centroid.toInstance())
            }
        }

        return result
    }

    /**
     * Creates a new cluster universe providing the same metadata than this one.
     *
     * @param relativeFrequencies
     * @param weight
     * @return the new empty universe.
     */
    DatasetSummary clone(Map<Double, Double> relativeFrequencies, int weight) {

        return new DatasetSummary(relativeFrequencies, metadata, weight)
    }

    double getAverageRecursionStepsProUpdate() {

        double result
        for (CentroidsForOneClass cfoc : values()) {

            result += cfoc.getAverageRecursionStepsProUpdate()
        }

        return result / values().size()
    }
}

/**
 * User: flangner
 * Date: 12.07.12
 * Time: 11:25
 *
 * A container for centroids representing all samples with the same label (class).
 * Adding ensures that the maximum amount of centroids is not exceeded.
 */
@Typed
class CentroidsForOneClass extends ArrayList<AuxiliaryCentroid>{

    private int openSlots
    private long recursionSteps = 0L
    private long updates = 0L

    /**
     * Default constructor. Initializes a new empty cluster with space for maxNumCentroids.
     *
     * @param maxNumCentroids
     */
    CentroidsForOneClass(int maxNumCentroids) {
        super(maxNumCentroids)

        this.openSlots = maxNumCentroids
    }

    /**
     * Adds an additional sample to this cluster.
     *
     * @param sample
     * @param summarizeStrategy - how to summarize samples.
     */
    void addSample(Instance sample, String summarizeStrategy) {

        addCentroid(new AuxiliaryCentroid(sample), summarizeStrategy)
    }

    /**
     * Adds an additional centroid to this cluster.
     *
     * @param centroid
     * @param summarizeStrategy - how to summarize samples.
     */
    void addCentroid(AuxiliaryCentroid centroid, String summarizeStrategy) {

        switch (summarizeStrategy) {
            case "Distance" :
                addCentroidByDistance(centroid)
                break

            case "Greedy" :
                addCentroidRandom(centroid)
                break

            default:
                throw new UnsupportedOperationException("Sample summarize-strategy '" + summarizeStrategy +
                        "' is unknown!")
        }
    }

    /**
     * Adds an additional centroid to this cluster using a randomized strategy.
     *
     * @param centroid
     */
    void addCentroidRandom(AuxiliaryCentroid centroid) {

        if (openSlots > 0) {

            add(centroid)

            openSlots--
        } else {

            get((int) findClosestCentroid(centroid)[0]).merge(centroid)
        }
    }

    /**
     * Adds an additional centroid to this cluster using a closest-distance strategy.
     *
     * @param centroid
     */
    void addCentroidByDistance(AuxiliaryCentroid centroid) {

        if (openSlots > 0) {

            final int nextOpenSlot = size()
            add(centroid)
            updateClosestDistance(nextOpenSlot)                 // this is the reason why the algorithm is not as linear
                                                                // to the number of samples as it used to be
                                                                // (Ek has also to be taken into account)

            openSlots--
        } else {

            final Tuple closestCentroid = findClosestCentroid(centroid)
            final int neighborOne = findClosestCentroids()

            // the new centroid will be merged to an already existing centroid
            if (neighborOne == -1 ||
                closestCentroid[1] < get(neighborOne).distanceToNearestNeighbor()) {

                final int slot = (int) closestCentroid[0]

                get(slot).merge(centroid)

                updates++
                updateClosestDistance(slot)

            // merging of two existing centroids will provide space the new centroid needs
            } else {

                final int neighborTwo = get(neighborOne).getNearestNeighbor()

                get(neighborOne).merge(get(neighborTwo))
                set(neighborTwo, centroid)

                updates += 2
                updateClosestDistance(neighborOne)
                updateClosestDistance(neighborTwo)
            }
        }
    }

    /**
     * Will return -1 if there is no pair to compare (e.g. the cluster consists of only one centroid).
     *
     * @return one centroid position of a pair of the closest centroids from within the current cluster.
     */
    private int findClosestCentroids() {

        Double smallestDistance = Double.POSITIVE_INFINITY
        int nearestNeighbor = -1
        Double distance

        for (int i = 0; i < size(); i++) {

            distance = new Double(get(i).distanceToNearestNeighbor())

            if (distance < smallestDistance) {

                smallestDistance = distance
                nearestNeighbor = i
            }
        }

        assert (nearestNeighbor == -1 ||
                smallestDistance == get(get(nearestNeighbor).getNearestNeighbor()).distanceToNearestNeighbor())

        return nearestNeighbor
    }

    /**
     * Calculates the closest centroid from the existing centroids to the given new centroid.
     *
     * @param centroid a new centroids not yet added to this cluster.
     * @return a tuple of the closest existing centroid and the distance between this and the new centroid.
     */
    private Tuple findClosestCentroid(AuxiliaryCentroid newCentroid) {

        Double smallestDistance = Double.POSITIVE_INFINITY
        int nearestNeighbor = -1
        Double distance

        // 1. find an existing centroid ("nearestNeighbor") with smallest distance to newCentroid
        for (int i = 0; i < size(); i++) {

            // compute the distance between the next centroid and the new centroid
            distance = new Double(get(i).squaredDistanceTo(newCentroid))

            if (distance < smallestDistance) {

                smallestDistance = distance
                nearestNeighbor = i
            }
        }

        return new Tuple(nearestNeighbor, smallestDistance)
    }

    /**
     * Keeps the minimum distance pair of centroids up to date on a insertion of the given centroid at the given
     * position.
     *
     * @param position
     */
    private void updateClosestDistance(int position) {

        // invalidate current nearest neighbor to ensure deterministic recursion for some special cases
        get(position).updateNearestNeighbor(-1, Double.POSITIVE_INFINITY)

        Double smallestDistance = Double.POSITIVE_INFINITY
        int nearestNeighbor = -1
        Double distance

        // calculate distances to centroids before the position of the given centroid
        for (int i = 0; i < size(); i++) {

            // the distance between to the centroid itself must not be calculated
            if (i != position) {

                distance = new Double(get(position).squaredDistanceTo(get(i)))

                // update the nearest neighbor of the current centroid if the new centroid is closer
                if (distance < get(i).distanceToNearestNeighbor()) {

                    get(i).updateNearestNeighbor(position, distance)
                }
                // if an old centroid gets replaced by the new one we need to find a nearest neighbor for the
                // because the nearest neighbor is an asymmetric relation
                else if (get(i).getNearestNeighbor() == position && distance > get(i).distanceToNearestNeighbor()) {

                    recursionSteps++
                    updateClosestDistance(i)
                }

                // update best distance
                if (distance < smallestDistance) {

                    nearestNeighbor = i
                    smallestDistance = distance
                }
            }
        }

        get(position).updateNearestNeighbor(nearestNeighbor, smallestDistance)
    }

    /**
     *
     * @return the average amount of recursion steps pro update measured for these clusters.
     */
    double getAverageRecursionStepsProUpdate() {

        return (updates == 0) ? 0 : (double) recursionSteps / updates
    }
}

/**
 * User: flangner
 * Date: 12.07.12
 * Time: 14:00
 *
 * An auxiliary centroid represents a bunch of samples of the original data set. It provides information about the
 * weight (the amount of samples it represents),
 * the centroid with the closest distance within the auxiliary cluster it belongs to
 * and of course the centroids coordinates.
 */
@Typed
class AuxiliaryCentroid implements Comparable<AuxiliaryCentroid> {

    private int weight
    private double[] coordinates

    // the value of classIndex has to be excluded during the merge operation
    private final classIndex

    // fields for calculating the closest centroids within a cluster
    private int nearestNeighbor = -1
    private double distanceToNearestNeighbor = Double.POSITIVE_INFINITY

    /**
     * This constructor copies the sample-data instance and removes Double.NaN occurrences.
     *
     * @param instance
     */
    AuxiliaryCentroid(Instance instance) {

        final double[] original = instance.toDoubleArray()
        this.coordinates = new double [original.length]
        for (int i = 0; i < original.length; i++) {

            this.coordinates[i] = (Double.isNaN(original[i])) ? 0.0 : original[i]
        }

        this.weight = instance.weight()
        this.classIndex = instance.classIndex()
    }

    /**
     * Replaces the current nearest neighbor with the one identified by its position.
     *
     * @param position
     * @param distance
     */
    void updateNearestNeighbor(int position, double distance) {

        this.nearestNeighbor = position
        this.distanceToNearestNeighbor = distance
    }

    /**
     * @return the position of the nearest neighbor.
     */
    int getNearestNeighbor() {

        return nearestNeighbor
    }

    /**
     * @return the distance to the nearest neighbor.
     */
    double distanceToNearestNeighbor() {

        return distanceToNearestNeighbor
    }

    /**
     * Calculates the squared distance between centroids.
     * For this particular method the exclusion of the class index will be ignored, because it will not influence the
     * calculated distance.
     *
     * @param other
     * @return the euclidean distance between this and the given auxiliary centroid.
     */
    double squaredDistanceTo(AuxiliaryCentroid other) {

        double result      // initializing this variable with 0.0 will make groovy going insane on big decimals
        for (int i = 0; i < coordinates.length; i++) {

            final double difference = (double) (this.coordinates[i] - other.coordinates[i])

            result += (double) (difference * difference)
        }

        // We skipped this for efficiency (because we only compare distances - their squares behave the same)
        // result = Math.sqrt(result)
        return result
    }

    /**
     * Merges with the given centroid. Only this centroid will change.
     *
     * @param other - the other centroid which will remain unchanged.
     */
    void merge(AuxiliaryCentroid other) {

        final int commonWeight = this.weight + other.weight

        final double thisShare = this.weight / (double) commonWeight
        final double otherShare = 1.0 - thisShare

        for (int i = 0; i < coordinates.length; i++) {

            if (i != classIndex) {

                this.coordinates[i] = (this.coordinates[i] * thisShare) + (other.coordinates[i] * otherShare)
            }
        }

        this.weight = commonWeight
    }

    /**
     * Converts the centroid to a weka Instance
     *
     * @return the weka instance.
     */
    Instance toInstance() {

        return new Instance(this.weight, this.coordinates)
    }

    @Override
    int compareTo(AuxiliaryCentroid o) {

        return weight.compareTo(o.weight)
    }
}
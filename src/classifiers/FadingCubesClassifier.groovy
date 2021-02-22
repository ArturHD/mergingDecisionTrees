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

import cubes.ClassCube

import cubes.Cube
import edu.pvs.batchrunner.ExperimentResult
import groovy.util.logging.Log
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances
import static experiment.Tools.loadArff

import cubes.BuildTreeAndGetBoxSet

import static experiment.Tools.createKSplits

import experiment.PerfUtils
import classifiers.mapreduce.FadingCubesClassifierWorker
import experiment.ExperimentResultSingletonHolder
import classifiers.mapreduce.FadingCubesClassifierMapper
import experiment.Tools
import classifiers.mapreduce.FadingCubesClassifierReducer

/**
 * Created by: flangner 
 * Date: 23.02.12
 * Time: 10:10
 */
@Log @Typed
class FadingCubesClassifier extends Classifier implements PerfUtils {

    // cube-collections for classification
    private Collection<ClassCube>[] cubes
    private FadingCubesClassifierWorker[] classifierWorkers
    private CompletionService<double[]> runner

    // default value
    private int k = 1
    private ExecutorService cwtp

    /**
     * Triggers building of the tree from a collection of boxes
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data
     */
    @Override
    void buildClassifier(Instances data) {

        // classifier is build from the given data
        if (cubes == null) {

            // parameters that might be found at ExperimentResult
            // they are initialized with default values
            ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
            this.k = (results) ? Integer.parseInt(results.Ek as String) : 1

            def t0 = tic()
            List splits = createKSplits(k, data, results)
            def t1 = tic()

            def builder = new BuildTreeAndGetBoxSet(results)

            // initialize workers
            def workers = new FadingCubesClassifierMapper[k];
            for (int i = 0; i < k; i++) {

                Instances split = (splits) ? splits.get(i) : loadArff("split${i}")
                workers[i] = new FadingCubesClassifierMapper(split, i, builder, true)
            }

            int outstandingResults = k
            def es = Tools.getExecutorService()
            CompletionService<Collection<ClassCube>[]> ecs = new ExecutorCompletionService<Collection<ClassCube>[]>(es)
            for (FadingCubesClassifierMapper w: workers)
                ecs.submit(w)
            while (outstandingResults > 1) {
                def reducer = new FadingCubesClassifierReducer(data, ecs.take().get(), ecs.take().get(), true)
                ecs.submit(reducer)
                outstandingResults--
            }
            assert outstandingResults == 1
            cubes = ecs.take().get()
            es.shutdown()

            // initialize classifier worker
            cwtp = Tools.getExecutorService()
            runner = new ExecutorCompletionService<double[]>(cwtp)
            classifierWorkers = new FadingCubesClassifierWorker[k]
            for (int i = 0; i < k; i++) {

                classifierWorkers[i] = new FadingCubesClassifierWorker(i, cubes[i], true)
            }

            def t2 = tic()

            classifiers.FadingCubesClassifier.log.info("FC: Splitting took ${toDiffString(t0, t1)} and building of all the $k cube-collections took ${toDiffString(t1, t2)}")

        }
    }


    @Override
    public void finalize() {
        super.finalize()
        cwtp.shutdownNow()
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

        double[] result

        ExperimentResult results = ExperimentResultSingletonHolder.getInstance()

        assert classifierWorkers: "Classifier has not been initialized!"

        for (int i = 0; i < k; i++) {
            classifierWorkers[i].setSample(sample)
            runner.submit(classifierWorkers[i])
        }

        final double scaling = (1.0 / k as double)

        int outstandingResults = k
        double[][] rawResult = new double[k][]
        while (outstandingResults > 0) {

            rawResult[--outstandingResults] = runner.take().get()
        }

        int numClasses = rawResult[0].length
        result = new double[numClasses]

        for (int i = 0; i < numClasses; i++) {

            for (int j = 0; j < k; j++) {
                result[i] += rawResult[j][i]
            }

            result[i] *= scaling
        }

        return result
    }

    // 1/1000
    private final static double MINIMAL_WEIGHT = 0.001 // Math.pow(10.0, (Double.MIN_EXPONENT + 2))

    /**
     * @param sample
     * @param cube
     * @return the probability of a sample having the same class like the cube it intersects. Gaussian probability
     *         distribution for points in the cube is assumed R e (0:1).
     */
    static double calculateSampleInCubeProbability(Instance sample, Cube cube) {

        assert cube.isInsideCube(sample): "Sample $sample is not in Cube ${cube}!"

        double result = MINIMAL_WEIGHT

        // check, because the calculation does not work with unbounded boxes
        if (!cube.missingAtLeastOneBound()) {

            // move center to 0 (vector) and adjust cube and sample coordinates
            double[] centerCoordinates = cube.getCenter()
            double[] sC = new double[centerCoordinates.length]
            System.arraycopy(sample.toDoubleArray(), 0, sC, 0, sC.length)
            double[] sampleCoordinates = move(sC, centerCoordinates)
            double[][] cubeRanges = move(cube.bounds, centerCoordinates)


            double distToCenter = calculateEuclideanDistance(sampleCoordinates)

            double totalDist = calculateEuclideanDistance(getIntersection(sampleCoordinates, cubeRanges))

            double weight = (totalDist - distToCenter) / totalDist

            // the probability may not be 0.0
            result = (weight != 0.0) ? weight : result
        }

        return result
    }

    /**
     * @param line
     * @param cube
     * @return the cutpoint of line and cube on the side of the sample.
     */
    private static double[] getIntersection(double[] line, double[][] cube) {

        assert line.length == cube.length: "Line $line and cube $cube have different dimensions!"

        def dim = line.length
        double[] cutpoint
        for (int hyperplaneDimension = 0; hyperplaneDimension < dim; hyperplaneDimension++) {

            // filter combinations where line and hyperplane are orthogonal
            if (line[hyperplaneDimension] != 0.0) {

                // toggle algebraic sign if necessary (possible because hyperplaneDistances are symmetric)
                double hyperplaneDistance = cube[hyperplaneDimension][0]
                if ((hyperplaneDistance < 0 && line[hyperplaneDimension] > 0) ||
                        (hyperplaneDistance > 0 && line[hyperplaneDimension] < 0)) {

                    hyperplaneDistance *= -1.0
                }

                double scalar = hyperplaneDistance / line[hyperplaneDimension]
                cutpoint = move(line, scalar)

                // we got the wrong hyperplane to intersect with, so let's try it with another one
                if (covers(cutpoint, cube)) {
                    return cutpoint
                }
            }
        }


        return null
        //assert false : "The line does not intersect the cube at all!" may happen if cube covers the complete input space
    }

    /**
     * Determines whether a point is covered by this cube.
     *
     * @param point - the point to check.
     * @param cube - the cube that might cover the point
     * @return true if the point is covered by the cube, false otherwise.
     */
    private static boolean covers(double[] point, double[][] cube) {

        assert point.length == cube.length: "Point $point and cube $cube have different dimensions!"

        // check coverage for all dimensions
        def dim = point.length
        for (int i = 0; i < dim; i++) {

            if (point[i] < cube[i][0] || cube[i][1] < point[i]) {
                return false
            }
        }

        return true
    }

    /**
     * @param point
     * @return the euclidean distance between 0 (vector) and the given point.
     */
    private static double calculateEuclideanDistance(double[] point) {

        if (point == null) return Double.POSITIVE_INFINITY

        def double sqEucDist = 0.0

        for (int i = 0; i < point.length; i++) {
            sqEucDist += Math.pow(point[i], 2.0)
        }

        return Math.sqrt(sqEucDist)
    }

    /**
     * @param point
     * @param scalar
     * @return the point with applied scalar.
     */
    private static double[] move(double[] point, double scalar) {

        def dim = point.length

        double[] result = new double[dim]

        for (int i = 0; i < dim; i++) {
            result[i] = point[i] * scalar
        }

        return result
    }

    /**
     * @param point
     * @param difference
     * @return point moved by the given distance.
     */
    private static double[] move(double[] point, double[] distance) {

        assert point.length == distance.length: "Point $point and difference $distance have different dimensions!"

        def dim = point.length
        double[] result = new double[dim]
        for (int i = 0; i < dim; i++) {
            result[i] = point[i] - distance[i]
        }

        return result
    }

    /**
     * @param range
     * @param difference
     * @return range moved by the given distance
     */
    private static double[][] move(double[][] range, double[] distance) {

        assert range.length == distance.length: "Point $range and difference $distance have different dimensions!"

        def dim = range.length
        double[][] result = new double[dim][]
        for (int i = 0; i < dim; i++) {

            result[i] = new int[2]
            if (range[i] == null) {

                result[i][0] = Double.NEGATIVE_INFINITY
                result[i][1] = Double.POSITIVE_INFINITY
            } else {

                assert range[i].length == 2: "Illegal range detected!"

                result[i][0] = range[i][0] - distance[i]
                result[i][1] = range[i][1] - distance[i]
            }

        }

        return result
    }

    @Override
    public String toString() {

        def result = new StringBuilder()
        cubes.each { cube -> result.append(cube).append("\n")}
        return result.toString()
    }
}

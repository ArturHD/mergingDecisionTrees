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

package experiment.generator;

import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class SampleGenerator {

    private final static int MIN_SHIFT = 0;
    private final static int MAX_SHIFT = 2;
    private final static String TARGET = "synthetic";
    private final static String CD_PREFIX = "cd_";
    private final static String FORMAT_CSV = ".csv";
    private final static String FORMAT_ARFF = ".arff";
    private final static String SEPARATOR = ",";

    /**
     * @param args
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 4) {
            usage();
            System.exit(1);
        }

        final Random random = new Random();

        FileWriter mWr = null;
        try {

            /////// parse input

            // overall number of samples to create
            final int n = Integer.parseInt(args[0]);

            // number of dimensions (class label does not count as attribute)
            final int dimension = Integer.parseInt(args[1]);

            // double value that indicates how much both classes tend to overlap (no overlapping means that there will
            // be no noise at all)
            final double noise = Double.parseDouble(args[2]);

            final int windowSize = Integer.parseInt(args[3]);
            assert (windowSize <= n);

            // create the class seeds
            double[] seedA = null;
            double[] seedB = new double[dimension];
            double[] AtoB = null;
            double[] sampleRange = new double[dimension];
            double distance = 0.0;


            seedA = randomSample(random, dimension);
            AtoB = randomSample(random, dimension);
            for (int i = 0; i < dimension; i++) {
                seedB[i] = seedA[i] + AtoB[i];

                sampleRange[i] = (AtoB[i] / 2.0) + noise * (AtoB[i] / 2.0);
            }

            distance = calculateEuclideanDistance(seedA, seedB);


            final double[] driftVectorA = randomDriftVector(random, dimension, distance * 1 / windowSize);
            final double[] driftVectorB = randomDriftVector(random, dimension, distance * 1 / windowSize);

            // some output for the user
            System.out.println("The seed for class A will be:\n" + Utils.arrayToString(seedA) + "\n" +
                    "and for class B:\n" + Utils.arrayToString(seedB));
            System.out.println("The drift vectorA is:\n" + Utils.arrayToString(driftVectorA));
            System.out.println("The drift vectorB is:\n" + Utils.arrayToString(driftVectorB));
            System.out.println(n + " samples are now written to the files '" + getFileName(false) + "' and '" +
                    getFileName(true) + "'...\n");

            // without cd
            mWr = new FileWriter(getFileName(false), false);
            mWr.write(header(dimension));
            mWr.write(sampleToString(seedA, "A"));
            mWr.write(sampleToString(seedB, "B"));
            final int samples = (n / 2) - 1;
            for (int i = 0; i < samples; i++) {
                mWr.write(sampleToString(newSample(seedA, random, sampleRange), "A"));
                mWr.write(sampleToString(newSample(seedB, random, sampleRange), "B"));
            }
            mWr.close();

            // with cd
            mWr = new FileWriter(getFileName(true), false);
            mWr.write(header(dimension));
            mWr.write(sampleToString(seedA, "A"));
            mWr.write(sampleToString(seedB, "B"));
            int windowStart = (samples / 2) - (windowSize / 2);
            int windowEnd = windowStart + windowSize / 2;
            for (int i = 0; i < windowStart; i++) {
                mWr.write(sampleToString(newSample(seedA, random, sampleRange), "A"));
                mWr.write(sampleToString(newSample(seedB, random, sampleRange), "B"));
            }
            int inWindowIndex = 0;
            for (int i = windowStart; i < windowEnd; i++) {
                mWr.write(sampleToString(newSample(drift(seedA, driftVectorA, inWindowIndex), random, sampleRange), "A"));
                mWr.write(sampleToString(newSample(drift(seedB, driftVectorB, inWindowIndex++), random, sampleRange), "B"));
            }
            double[] driftA = drift(seedA, driftVectorA, inWindowIndex);
            double[] driftB = drift(seedB, driftVectorB, inWindowIndex);
            for (int i = windowEnd; i < samples; i++) {
                mWr.write(sampleToString(newSample(driftA, random, sampleRange), "A"));
                mWr.write(sampleToString(newSample(driftB, random, sampleRange), "B"));
            }
            mWr.close();

            convert(false);
            convert(true);

            System.out.println("... done!");

        } catch (Exception io) {

            System.err.println("ERROR: " + io.getMessage());
            usage();
            System.exit(1);
        } finally {

            if (mWr != null) mWr.close();
        }
    }

    private static String header(int dimension) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < dimension; i++) {
            result.append("d").append(i).append(SEPARATOR);
        }
        result.append("Class\n");

        return result.toString();
    }

    private static void convert(boolean cd) throws IOException {
        CSVLoader csvl = new CSVLoader();
        csvl.setSource(new File(getFileName(cd)));
        ArffSaver arfs = new ArffSaver();
        Instances dataSet = csvl.getDataSet();
        arfs.setInstances(dataSet);
        arfs.setFile(new File("data/arff/" + getOutputFileName(cd)));
        arfs.writeBatch();
    }

    /**
     * @param cd
     * @return the fileName composed from filename templates.
     */
    private static String getFileName(boolean cd) {
        return ((cd) ? CD_PREFIX : "") + TARGET + FORMAT_CSV;
    }

    private static String getOutputFileName(boolean cd) {
        return ((cd) ? CD_PREFIX : "") + TARGET + FORMAT_ARFF;
    }


    /**
     * @param sample
     * @param classLabel
     * @return a string representation of the given sample using classLabel.
     */
    private static String sampleToString(double[] sample, String classLabel) {

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < sample.length; i++) {
            builder.append(sample[i]).append(SEPARATOR);
        }

        builder.append(classLabel).append("\n");

        return builder.toString();
    }

    /**
     * @param random
     * @param dimension
     * @return a random sample created independently from other samples. Might be used as seed for clusters.
     */
    private static double[] randomSample(Random random, int dimension) {

        double[] result = new double[dimension];

        for (int i = 0; i < dimension; i++) {

            result[i] = random.nextDouble() * randomShift(random);
        }

        return result;
    }

    /**
     * @param random
     * @param dimension
     * @return a random sample created independently from other samples. Might be used as seed for clusters.
     */
    private static double[] randomDriftVector(Random random, int dimension, double maxLength) {

        double[] result = new double[dimension];

        for (int i = 0; i < dimension; i++) {

            result[i] = random.nextDouble() * 2.0 - 1.0;
        }


        Utils.normalize(result);

        for (int i = 0; i < dimension; i++) {

            result[i] *= maxLength;
        }

        return result;
    }

    /**
     * @param seed
     * @param driftVector - if null seed will be returned.
     * @param scalar
     * @return the sum of seed and driftVector, if not null, seed otherwise.
     */
    private static double[] drift(double[] seed, double[] driftVector, int scalar) {

        if (driftVector == null) {
            return seed;
        }

        assert (seed.length == driftVector.length);

        double[] result = seed.clone();
        for (int i = 0; i < result.length; i++) {
            result[i] += scalar * driftVector[i];
        }

        return result;
    }

    /**
     * @param random
     * @return a random multiplicator for shifting samples.
     */
    private static double randomShift(Random random) {

        return Math.pow(10, MIN_SHIFT + random.nextInt(MAX_SHIFT));
    }

    /**
     * @param point1
     * @param point2
     * @return the euclidean distance between 0 (vector) and the given point.
     */
    private static double calculateEuclideanDistance(double[] point1, double[] point2) {

        if (point1 == null || point2 == null) return Double.POSITIVE_INFINITY;

        assert (point1.length == point2.length) : "Point: " + point1 + " and point: " + point2 +
                " have different dimensions.";

        double sqEucDist = 0.0;

        for (int i = 0; i < point1.length; i++) {
            sqEucDist += Math.pow((point1[i] - point2[i]), 2.0);
        }

        return Math.sqrt(sqEucDist);
    }

    /**
     * @param seed
     * @param random
     * @param sampleRange
     * @return a new random sample created from the given seed with a limited distance on each dimension to this seed
     *         using the given random-generator.
     */
    private static double[] newSample(double[] seed, Random random, double[] sampleRange) {

        double[] result = seed.clone();

        for (int i = 0; i < result.length; i++) {

            result[i] += (2.0 * random.nextDouble() - 1.0) * sampleRange[i];
        }

        return result;
    }

    /**
     * Prints the usage information.
     */
    public static void usage() {
        System.out.println("usage: SampleGenerator <n> <d> <noise> <windowSize>\n"
                + "    <n>: number of samples to generate\n"
                + "    <d>: number of attributes (dimensions) of the samples w/o\n"
                + "    <noise>: ratio of overlapping of the sample clusters, where 0.0 means no overlapping (esp.\n"
                + "             no noise) and 1.0 means full overlapping (the cluster centers are identical)\n"
                + "    <windowsSize>");
    }
}

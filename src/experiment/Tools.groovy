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

package experiment

import cubes.ClassCube
import edu.pvs.batchrunner.ExperimentResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import weka.classifiers.Classifier
import weka.classifiers.Evaluation
import weka.classifiers.trees.J48
import weka.core.Instances
import weka.core.Range
import weka.core.converters.ConverterUtils.DataSource
import weka.filters.Filter
import weka.filters.supervised.attribute.Discretize
import weka.filters.unsupervised.instance.RemovePercentage
import weka.filters.unsupervised.instance.RemoveRange
import static weka.filters.Filter.useFilter
import cubes.Cube
import weka.core.Instance
import weka.filters.unsupervised.instance.Resample

/**
 * Created by: Artur Andrzejak 
 * Date: 05.02.12
 * Time: 12:58
 * Tools for handling experiments w/ WEKA and boxes
 * todo: Split into "WekaTools" and "Tools" (not Weka-related)
 */

@Typed
class Tools {

    @Deprecated //Use BuildTreeAndGetBoxSet instead.
    static J48 buildModelJ48(Instances dataset, Map classifierParameters = null) {
        if (classifierParameters != null) {
            // todo: handle J48 params - e.g. pruning level
        }
        // create the model
        def j48 = new J48()
        j48.buildClassifier(dataset)
        return j48
    }

    @Typed
    static Instances loadArff(String datasetName, int classIndex = -1) {
        DataSource source = new DataSource(datasetName);
        Instances data = source.getDataSet();
        if (classIndex < 0) {
            // setting class attribute if the data format does not provide this information
            if (data.classIndex() == -1)
                data.setClassIndex(data.numAttributes() - 1)             // default classIndex is (#Atts-1)
        } else
            data.setClassIndex(classIndex)
        return data
    }

    @Typed
    static Map splitInstances(Instances input, double percentageForTraining) {
        // See http://old.nabble.com/Dividing-data-set-into-training,-validation-and-testing-set-td14697677.html
        assert 0 <= percentageForTraining && percentageForTraining <= 100, "Wrong split percentage supplied (percentage = $percentageForTraining)"

        // Get the training set
        Instances trainInstances = removePercentageOfInstances(input, (100.0 - percentageForTraining), false)

        boolean invertSelection = true
        // get the test set
        Instances testInstances = removePercentageOfInstances(input, (100.0 - percentageForTraining), invertSelection)

        return [training: trainInstances, test: testInstances]
    }

    public static Instances removePercentageOfInstances(Instances input, double percentageToRemove, boolean takeSecondPart = false) {
        def filter = new RemovePercentage()
        filter.setPercentage(percentageToRemove)
        filter.setInputFormat(input)
        filter.setInvertSelection(takeSecondPart) // if true, complement of first part is taken
        Instances testInstances = filter.useFilter(input, filter)
        return testInstances
    }

    /**
     * Splits instances dataset into k parts. If ER are provided, additional settings are used
     * @param k - number of splits to create.
     * @param data - may be null, if splits are loaded from ARFF file.
     * @param results - config.
     * @return a list of Instances, if data != null.
     */
    @Typed
    static List<Instances> createKSplits(Integer k, Instances data, ExperimentResult results = null) {

        if (!data) return null

        if (results) {
                splitIntoDkInstances(data, k, results.getDouble("Esim"), results.getBool("EuseSameRandomSamples"))
        } else
                splitIntoDkInstances(data, k)

    }

    /** Resamples an input set of instances down to newSampleSizePercent
     * @param inputInstances
     * @param newSampleSizePercent output size as percentage of the input
     * @return resampled set of instances with possibly reduced size
     */
    static Instances getResampledSubset(Instances inputInstances, double newSampleSizePercent) {
        assert 0 <= newSampleSizePercent && newSampleSizePercent <= 100, "Wrong percentage supplied (percentage = $newSampleSizePercent)"

        def filter = new Resample()
        filter.setSampleSizePercent(newSampleSizePercent)
        filter.setInputFormat(inputInstances)
        Instances resultingInstances = filter.useFilter(inputInstances, filter)

        return resultingInstances
    }

    /**
     * Splits training data into k Di-chunks consisting of a disjunct range and the given percentage of similar samples.
     * Each D_i consists of numUnlikeInstancesPerDi samples disjunct from samples in other D_i's and numSimilarInstancePerDi samples
     * "similar" to samples in other D_i's (see below). We have numInstancesPerDi =
     *  numUnlikeInstancesPerDi + numSimilarInstancePerDi, and numInstancesPerDi = input.size()/k.
     *  The ratio numSimilarInstancePerDi / numInstancesPerDi is exactly as determined by the value of similarityFraction.
     *  If useSameRandomSamples is true, the "similar-parts" of all D_i's consist of the same samples (sampled randomly from
     *  a common pool) - i.e. this is a "stronger" similarity. Otherwise the "similar-parts" of all D_i's are  sampled
     *  with repetition from a common pool of size (input.size())*similarityFraction.
     *
     * @param input instances to be split
     * @param k number of data fragments to create
     * @param similarityFraction ratio numSimilarInstancePerDi / numInstancesPerDi
     * @param useSameRandomSamples
     * @return k Di splits with a given similarity percentage using the same random samples for each Di if set.
     */
    @Typed
    static List<Instances> splitIntoDkInstances(Instances input, Integer k = 1, Double similarityFraction = 0.0, Boolean useSameRandomSamples = false) {
        assert 0.0 <= similarityFraction && similarityFraction <= 1.0, "Wrong similarity percentage supplied (percentage = $similarityFraction)"

        List<Instances> result = new ArrayList<Instances>(k);

        int numInstances = input.numInstances()
        int numInstancesPerDi = Math.floor(numInstances / k) as int
        int numUnlikeInstancesPerDi = Math.floor(numInstancesPerDi * (1.0 - similarityFraction)) as int
        int numSimilarInstancePerDi = numInstancesPerDi - numUnlikeInstancesPerDi as int

        // fill each split i with numUnlikeInstancesPerDi instances building the i-th block from the begin of "input"
        for (int i = 0; i < k; i++) {

            // indices of the instance "block" which is copied is "disjunct" part in D_i
            int startIndexBlock = i * numUnlikeInstancesPerDi + 1
            int endIndexBlock = (i + 1) * numUnlikeInstancesPerDi

            if (similarityFraction < 1.0 && (startIndexBlock < endIndexBlock)) {
                String range = "${startIndexBlock}-${endIndexBlock}"

                def filter = new RemoveRange()
                filter.setInstancesIndices(range)
                filter.setInvertSelection(true)
                filter.setInputFormat(input)
                result.add(useFilter(input, filter))
            } else
                result.add(input)
        }

        // add numSimilarInstancePerDi to each of the Di's splits if necessary
        if (similarityFraction > 0.0 && numSimilarInstancePerDi > 0) {

            // todo: check - are the removed instances exactly those stored in result in the loop above?
            // Remove all "blocks" of disjunct instances; the reminder is a pool for similar instances
            def filter = new RemovePercentage()
            filter.setPercentage similarityFraction * 100.0
            filter.setInvertSelection(true)
            filter.setInputFormat(input)
            Instances randomSamples = useFilter(input, filter)

            def numRandomInstances = randomSamples.numInstances()

            if (numRandomInstances <= 0) {
                println "Bad case: numRandomInstances <= 0"
                assert numRandomInstances > 0, "There are no samples left to use as random samples."
            }
            Random random = new Random()

            for (int n = 0; n < numSimilarInstancePerDi; n++) {

                if (useSameRandomSamples) {

                    def randomIndexFromPool = random.nextInt(numRandomInstances)
                    def instance = randomSamples.instance(randomIndexFromPool)

                    for (Instances D_i: result) {
                        D_i.add(instance)
                    }
                } else {

                    for (Instances D_i: result) {

                        def randomIndexFromPool = random.nextInt(numRandomInstances)
                        def instance = randomSamples.instance(randomIndexFromPool)

                        D_i.add(instance)
                    }
                }
            }
        }

        return result
    }


    @Typed
    static Map evaluateTestset(Classifier trainedModel, Instances testData, boolean getOutputDistribution = false) {
        // see WekaGroovyExamples/UsingJ48Ext.groovy
        def evaluation = new Evaluation(testData)
        def predictionsBuffer = new StringBuffer()  // predictionsBuffer for predictions
        def attRange = new Range()  // no additional attributes to output - AA: what does this mean?
        evaluation.evaluateModel(trainedModel, testData, predictionsBuffer, attRange, getOutputDistribution)

        return [evaluation: evaluation, predictions: predictionsBuffer]
    }


    @Typed
    static def printEvaluationResults(Classifier trainedClassifier, Map evalResultMap, boolean printPredictions = false) {
        println "--> Generated model:\n"
        println trainedClassifier

        println "--> Evaluation:\n"
        Evaluation evaluation = evalResultMap.evaluation
        println evaluation.toSummaryString()

        if (printPredictions) {
            println "--> Predictions:\n"
            println evalResultMap.predictions
        }
    }

    @Typed
    static double[][] discretizeToNumeric(Instances data) {
        def filter = new Discretize()
        filter.setInputFormat(data)
        Filter.useFilter(data, filter)

        final int numAttribues = data.numAttributes()
        double[][] perAttCutpoints = new double[numAttribues][]

        for (int attIndex = 0; attIndex < numAttribues; attIndex++) {
            perAttCutpoints[attIndex] = filter.getCutPoints(attIndex)
        }

        // re-labeling the instances
        for (int instanceIndex = 0; instanceIndex < data.numInstances(); instanceIndex++) {
            for (int attIndex = 0; attIndex < numAttribues; attIndex++) {
                double[] cutpoints = perAttCutpoints[attIndex]
                if (cutpoints) {
                    double attrValue = data.instance(instanceIndex).value(attIndex)
                    int binIndex = findBinIndex(cutpoints, attrValue)
                    data.instance(instanceIndex).setValue(attIndex, binIndex)
                }
            }
        }
        return perAttCutpoints
    }


    // Discretize the instances by Weka methods (either supervised or not) and returns the discretized instances
    static Instances discretizeInstances(Instances input, boolean supervisedDiscretize = true) {
        def filter
        if (supervisedDiscretize) {
            filter = new weka.filters.supervised.attribute.Discretize()
        } else
            filter = new weka.filters.unsupervised.attribute.Discretize()

        filter.setInputFormat(input);
        def result = Filter.useFilter(input, filter)
        return result
    }

    // Finds the index of the bin containing value. binLimits should be sorted in asc. order
    // todo: replace linear search via bin. search from java.util. etc.
    @Typed
    static int findBinIndex(double[] binLimits, double value) {
        int binStart = 0
        while (binStart < binLimits.length && binLimits[binStart] < value) {
            binStart++
        }
        return binStart
    }

    /** Merge two instance sets.
     * @param instances1
     * @param instances2
     * @return the merged instance sets
     * @author Eric Eaton, http://cs.brynmawr.edu/~eeaton/software.html
     */
    @Typed
    public static Instances mergeInstances(Instances instances1, Instances instances2) {
        if (instances1 == null)
            return instances2;
        if (instances2 == null)
            return instances1;
        if (!instances1.checkInstance(instances2.firstInstance()))
            throw new IllegalArgumentException("The instance sets are incompatible.");
        Instances mergedInstances = new Instances(instances1);
        Instances tempInstances = new Instances(instances2);
        for (int i = 0; i < tempInstances.numInstances(); i++) {
            mergedInstances.add(tempInstances.instance(i));
        }
        return mergedInstances;
    }


    // Computes a bounding box for a set of instance, i.e. for each dim the min and max value of attribute at dim
    // todo: This assumes that we have a class as one of the attributes
    static Cube getBoundingBox(Instances instances) {

        int classIndex = instances.classIndex()
        // if classIndex < 0 there is no class attribute, so correct the # of dimensions
        int nDims = instances.numAttributes() - 1
        if (classIndex < 0)
            nDims++

        Cube bb = new Cube(nDims)   // our bounding box
        double newLower, newUpper
        // double [][] bounds = new double [nDims][2]  // indexing: (dimension, {lower = 0, upper = 1})

        for (int instanceIndex in 0..<instances.numInstances()) {
            Instance instance = instances.instance(instanceIndex)
            int dim = 0
            for (int i = 0; i < instances.numAttributes(); i++) {
                // skip the class attribute
                if (dim == classIndex)
                    break

                double attributeVal = instance.value(i)
                // obviously we get sometimes NaN as an attribute value, skip such attribute
                if (Double.isNaN(attributeVal)) {
                    break
                }

                // set the lower bound
                double previousLower = bb.getLower(dim)
                if (previousLower <= Double.NEGATIVE_INFINITY)
                    newLower = attributeVal
                else
                    newLower = Math.min(previousLower, attributeVal)
                bb.setLower(dim, newLower)

                // set the upper bound
                double previousUpper = bb.getUpper(dim)
                if (previousUpper >= Double.POSITIVE_INFINITY)
                    newUpper = attributeVal
                else
                    newUpper = Math.max(previousUpper, attributeVal)
                bb.setUpper(dim, newUpper)

                dim++
            }
        }
        return bb
    }

    // Creates classifier instance from string classifierName.
    // For J48 and RandomForest only class name (w/out full package path) can be used
    public static Classifier getClassifierInstanceFromClassName(String classifierName) {
        // Add package names to get full qualified class name
        switch (classifierName) {
            case "J48":
                classifierName = "weka.classifiers.trees." + classifierName
                break
            case "RandomForest":
                classifierName = "weka.classifiers.trees." + classifierName
                break
        }
        final Classifier result
        Class classOfTheClassifier
        try {
            classOfTheClassifier = Class.forName(classifierName)
        } catch (ClassNotFoundException e) {
            classOfTheClassifier = null
            assert false, "Could not instantiate a classifier with class name '$classifierName', exception: $e"
        }
        result = (Classifier) classOfTheClassifier.newInstance()
        return result
    }


    // ----------- Non-Weka routines --------------------------

    /**
     * Creates a new ExecutorService which executes as many tasks concurrently as the ExperimentResult.Eparallel dictates
     * @return a thread pool executor
     */
    static ExecutorService getExecutorService() {
        ExperimentResult er = ExperimentResultSingletonHolder.getInstance()
        int tps = Integer.parseInt(er.Eparallel as String)
        if (tps > 0)
            return Executors.newFixedThreadPool(tps)
        else
            return Executors.newCachedThreadPool()
    }
    
    static int getHashSum(Collection<ClassCube> cubes) {

        int result = 0

        for (ClassCube cube : cubes) {
            result += cube.hashCode()
        }
            
        return result
    }
    
    static double createHashSum(Instances instances) {

        double result = 0
        
        for (int i = 0; i < instances.numInstances(); i++ ) {
            result += instances.instance(i).classValue()
        }
        
        return result
    }
}

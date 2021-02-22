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

package trees

import cubes.ClassCube
import cubes.Cube
import groovy.util.logging.Log
import weka.classifiers.trees.j48.Distribution
import weka.classifiers.trees.j48.GainRatioSplitCrit

/**
 * Created by: Artur Andrzejak 
 * Date: 05.02.12
 * Time: 19:13
 * Base class for splitpoint optimization at scanning one dimension of a cube collection. Methods are called 
 * by a scanner such as in trees.NodeModel#scanBoxesOneDim(int)
 */
@Log @Typed
abstract class SplitOptimizer {

    // Types of optimization criteria when searching for the best split:
    // MIN_NUM_SPLITS: min number of cuts through other cubes by a hyperplane spanned by a boundary (set by splitpoint X a dim)
    // INFO_GAIN: information gain (without weighting of cube size or original # of instances there
    // others: not implemented yet
    static enum SplitSearchType { /* default */ MIN_NUM_SPLITS, INFO_GAIN, INFO_GAIN_WEIGHTED_BY_INSTANCE_NUM
    }


    def HashSet<Cube> activeSet = new HashSet<Cube>()

    def List<Cube> bestActiveSet

    double bestCutpoint = Double.NaN

    double minCriterionValue = Double.MAX_VALUE

    int numUnbounded = -1

    int numBags = -1
    int numClasses = -1

    // for InfoGainOptimizer only
    int[] classFrequenciesBounded

    def reset(int newNumUnbounded = -1, int[] newClassFrequenciesBounded = null) {
        bestCutpoint = Double.NaN
        minCriterionValue = Double.MAX_VALUE
        bestActiveSet = null
        activeSet.clear()
        this.numUnbounded = newNumUnbounded
        this.classFrequenciesBounded = newClassFrequenciesBounded
    }

    /*
    The following three routines are called during a scan from trees.NodeModel.scanBoxesOneDim()
    The order per new cutpoint is: 1. removeEndingBoxes(), 2. testForNewOptimum(), 3. addStartingBoxes()
     */

    def addStartingBoxes(List<ClassCube> starting) {
        activeSet.addAll starting
    }

    def removeEndingBoxes(List<ClassCube> ending) {
        // Changed because java's java.util.AbstractSet.removeAll is implemented inefficiently:
        // if ending (a list) is larger than activeSet (should not be!), each element of activeSet is scanned in ending
        //  => (linear search in ending)*(activeSet.size())
        // activeSet.removeAll ending
        int before = activeSet.size()
        for (ClassCube cube in ending) {
            activeSet.remove(cube)
        }
        int after = activeSet.size()
        int endingSize = ending.size()
        assert endingSize <= before, "Anomaly at optimizer: removing ending boxes. activeSet (before, after) = $before, $after; #ending = ${ending.size()}"
    }

    def testForNewOptimum(double cutpoint) {
        throw new InternalError("SplitOptimizer needs to be subclassed for use")
    }

    protected setNewMinimum(double newCriterionValue, double cutpoint) {
        minCriterionValue = newCriterionValue
        bestCutpoint = cutpoint
        bestActiveSet = new ArrayList<Cube>(activeSet)   // don't need deep clone here
    }


    def double getBestSplitpoint() {
        return bestCutpoint
    }

    def List<Cube> getBestActiveSet() {
        return bestActiveSet
    }

    def double getMinCriterionResult() {
        return minCriterionValue
    }

    // A static "constructor" method
    static SplitOptimizer create(SplitSearchType strategyType, int newNumBags = -1, int newNumClasses = -1) {
        SplitOptimizer optimizer

        switch (strategyType) {

            case SplitOptimizer.SplitSearchType.MIN_NUM_SPLITS:
                optimizer = new SplitOptimizer.MinSplitCubesOptimizer()
                break

            case SplitSearchType.INFO_GAIN:
                optimizer = new SplitOptimizer.InfoGainOptimizer()
                break

            case SplitSearchType.INFO_GAIN_WEIGHTED_BY_INSTANCE_NUM:
                throw new UnsupportedOperationException("TODO: INFO_GAIN_WEIGHTED_BY_INSTANCE_NUM")
                break
        }

        optimizer.numBags = newNumBags
        optimizer.numClasses = newNumClasses

        return optimizer
    }

    // Implements following splitpoint search strategy of trees.NodeModel.SplitSearchType
    // MIN_NUM_SPLITS: min number of cuts through other cubes by a hyperplane spanned by a boundary (set by splitpoint X a dim)
    @Typed static class MinSplitCubesOptimizer extends SplitOptimizer {

        // minCriterionValue = Min. size of active set encountered - used for as the optimization criterion for MIN_NUM_SPLITS

        @Override
        def testForNewOptimum(double cutpoint) {
            int currentIntersectionSize = activeSet.size()
            if (minCriterionValue > currentIntersectionSize) {
                setNewMinimum(currentIntersectionSize, cutpoint)
            }
        }

        @Override
        def double getMinCriterionResult() {
            return numUnbounded + minCriterionValue
        }
    }

    // Implements following splitpoint search strategy of trees.NodeModel.SplitSearchType
    // INFO_GAIN: information gain (without weighting of cube size or original # of instances there
    @Typed static class InfoGainOptimizer extends SplitOptimizer {
        // minCriterionValue = reciprocal of InfoGain

        // perBagPerClass[bagIndex][classIndex] - as in j48.Distribution.Distribution(double [][] table)
        // perBagPerClass[0] is the class freq. distribution for lower part (left or below the splitpoint)
        // perBagPerClass[1] is the class freq. distribution for upper part (right or above the splitpoint)
        double[][] perBagPerClass

        // We use weka.classifiers.trees.j48.GainRatioSplitCrit.splitCritValue(Distribution bags) to compute info gain
        static GainRatioSplitCrit splitCriterionTool = new GainRatioSplitCrit()

        // We created Distribution via constructor weka.classifiers.trees.j48.Distribution.Distribution(double [][] table)
        // where obviously (analysed from code): perBagPerClass[bagIndex][classIndex]
        // optimize this later by computing InfoGain "in place"
        protected double computeInfoGain() {
            // 1. Set up instance -
            Distribution dist = new Distribution(perBagPerClass)
            return splitCriterionTool.splitCritValue(dist)
        }

        private setUpData() {
            if (perBagPerClass == null) {
                assert numBags > 0, "numBags must be set before using InfoGainOptimizer"
                assert numClasses > 0, "numClasses must be set before using InfoGainOptimizer"
                perBagPerClass = new double[numBags][numClasses]
            }
        }

        @Override
        def reset(int newNumUnbounded, int[] newClassFrequenciesBounded) {
            super.reset(newNumUnbounded, newClassFrequenciesBounded)
            perBagPerClass = null
            setUpData()
            // we need to copy classFrequenciesBounded  to perBagPerClass[1] because at first all boxes are above current cutpoint
            perBagPerClass[1] = (double[]) classFrequenciesBounded
        }

        def addStartingBoxes(List<ClassCube> starting) {
            activeSet.addAll starting
            // add classFreq to perBagPerClass[0] (the staring boxes are now left and right of splitpoint)
            countFrequenciesAndUpdate(starting, perBagPerClass[0], +1)
        }

        def removeEndingBoxes(List<ClassCube> ending) {
            activeSet.removeAll ending
            // remove classFreq count from perBagPerClass[1] (the ending boxes are transferred left of splitpoint)
            countFrequenciesAndUpdate(ending, perBagPerClass[1], -1)
        }

        def countFrequenciesAndUpdate(List<ClassCube> boxList, double[] targetArray, int posOrNegCount) {
            for (cube in boxList) {
                int classIndex = (int) cube.getClassValue()
                targetArray[classIndex] += posOrNegCount
            }
        }

        @Override
        def testForNewOptimum(double cutpoint) {
            double currentInfoGainReciprocal = computeInfoGain()
            // debug
            // println "InfoGain found: $currentInfoGainReciprocal"
            if (currentInfoGainReciprocal == Double.MAX_VALUE) {
                log.fine("InfoGain of value Double.MAX_VALUE found: boxes below and above splitpoint have same classes. Use cubes.JoinAdjacentCubes before building tree (perBagPerClass = $perBagPerClass)")
                currentInfoGainReciprocal /= 2
            }
            if (minCriterionValue > currentInfoGainReciprocal) {
                // new minimum found
                setNewMinimum(currentInfoGainReciprocal, cutpoint)
            }

        }
    }

}

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

import cubes.JoinAdjacentCubes
import weka.core.Instances
import static experiment.Tools.*

import cubes.BuildTreeAndGetBoxSet
import classifiers.MergedTreeClassifier

/**
 * + AA (15.01.2012) - extracting paths from J48 tree
 * AA: tested with iris.arff and segment-challenge.arff
 */

@Typed
class MergedTreeClassifierTest extends GroovyTestCase {

    final String irisDatasetFileName = new String("data/arff/iris.arff") // static declaration creates null! => compiler error?

    final int percentageTrainingSet = 80

    void testMergedTreeClassifierSimple() {

        // Get cubes from Iris dataset
        def irisArff = loadArff(irisDatasetFileName)
        Map splitData = splitInstances(irisArff, percentageTrainingSet)
        Instances trainingDataset = splitData.training
        Instances testDataset = splitData.test

        def j48 = buildModelJ48(trainingDataset)
        //println "J48 model: $j48"
        def j48result = evaluateTestset(j48, testDataset, true)
        println "################### J48 results:"
        printEvaluationResults(j48, j48result)


        MergedTreeClassifier mergedTreeFromCubes = new MergedTreeClassifier()
        mergedTreeFromCubes.buildClassifier(trainingDataset)

        def result = evaluateTestset(mergedTreeFromCubes, testDataset, true)

        println "################### MergedTreeClassifier results:"
        printEvaluationResults(mergedTreeFromCubes, result)

    }

    void testJoiner() {

        def irisArff = loadArff(irisDatasetFileName)
        def irisTrainCubes = new BuildTreeAndGetBoxSet().buildCubes(irisArff)
        // Joiner test - redundant here
        def joiner = new JoinAdjacentCubes(irisTrainCubes)
        def cubes = joiner.joinAdjacentCubes()
        println "Joined cubes from J48: ${joiner.totalJoinedCubes}"

    }

}
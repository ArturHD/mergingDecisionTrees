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

import edu.pvs.batchrunner.ExperimentResult
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances
import weka.core.Utils
import static experiment.Tools.loadArff

import cubes.BuildTreeAndGetBoxSet

import static experiment.Tools.createKSplits
import experiment.ExperimentResultSingletonHolder
import cubes.BoxSet

/**
 * Dummy classifier to calculate the concept drift.
 * May not be used for classification!
 * This classifier is used to count the number of conflicting cubes.
 *
 * Created by: flangner 
 * Date: 08.02.12
 * Time: 15:41
 */
@Typed
class ConceptDriftClassifier extends Classifier {


    private int k = 1

    /**
     * Triggers building of the tree from a collection of boxes
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data
     */
    @Override
    void buildClassifier(Instances data) {
        // parameters that might be found at ExperimentResult
        // they are initialized with default values
        ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
        this.k = Integer.parseInt(results.Ek as String)

        // initialize structures
        double[][] result = new double[k][k]
        BoxSet[] cubesForDi = new BoxSet[k]
        for (int i = 0; i < k; i++) {
            cubesForDi[i] = new ArrayList<ClassCube>(0)
        }

        def splits = createKSplits(k, data, results)

        // merge classifier for the training data
        def builder = new BuildTreeAndGetBoxSet(results)
        for (int i = 0; i < k; i++) {

            Instances split = (splits) ? splits.get(i) : loadArff("split${i}")
            cubesForDi[i] = builder.buildCubes(split)
            (results.cubesBuilt as List<Integer>) << cubesForDi[i].size()

        }
        for (int i = 0; i < k; i++) {
//  TODO seems to be useless, because we will drop results after merge
//            // join adjacent cubes after final merge if necessary
//            if (results && results.Pmerg == 1) {
//
//                def joiner = new JoinAdjacentCubes(cubesForDi[i])
//                cubesForDi[i] = joiner.joinAdjacentCubes()
//
//                //results.mergedBoxesCount += joiner.totalJoinedCubes
//            }

            // merge and count conflict boxes TODO
            for (int j = 0; j < k; j++) {

                // merge
                BoxSet container = new BoxSet(cubesForDi[i])
                container.mergeBoxSetsViaIntersections(cubesForDi[j])

                // count
                int conflict = 0
                for (classCube in container) {
                    if (classCube.classData.hasConflict) {
                        conflict++
                    }
                }
                result[i][j] = conflict // ((conflict as double) / (container.cubeCollection.size() as double)) * 100.0
            }
        }

        results.conflictBoxes = conflictBoxToString(result)
    }

    /**
     * @param ratioConflictBoxes
     * @return a comma-free representation of a conflict-box-ratio matrix.
     */
    String conflictBoxToString(double[][] ratioConflictBoxes) {
        StringBuilder builder = new StringBuilder("[")

        for (int i = 0; i < ratioConflictBoxes.length; i++) {
            builder.append("[ ")
            for (int j = 0; j < ratioConflictBoxes[i].length; j++) {
                builder.append(Utils.doubleToString(ratioConflictBoxes[i][j], 2)).append("; ")
            }
            builder.append(" ];")
        }

        builder.append("]")

        return builder.toString()
    }


    /**
     * Predicts the class memberships for a given instance.
     */
    @Override
    public double[] distributionForInstance(Instance instance) throws Exception {

        throw new UnsupportedOperationException("""The concept drift classifier is does not provide classification at all.
           It will only measure the number of conflict boxes that occure on permutations of merges of two cube-sets.""")
    }
}
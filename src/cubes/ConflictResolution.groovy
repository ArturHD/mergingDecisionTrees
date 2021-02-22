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

package cubes

import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import classifiers.MergedTreeClassifier
import weka.core.Instances
import weka.core.Utils
import static experiment.Tools.loadArff
import static weka.filters.Filter.useFilter

/**
 * Created by: Artur Andrzejak, Felix Langner
 * Date: 12.02.12
 * Time: 21:22
 * Code for conflict resolution (initially moved Felix routine resolveConflict from ClassData.groovy)
 */
@Typed
class ConflictResolution {


    static enum ConflictResolutionType {
        HIGHEST_CONFIDENCE_CLASS /* default */, MERGE_DISTRIBUTIONS, MAJORITY_CLASS, RECURSIVE_SUBDIVISION
    }

    protected Instances trainingData
    protected Collection<ClassCube> cubes
    ExperimentResult results



    def ConflictResolution(Instances newTrainingData, Collection<ClassCube> newCubes) {
        trainingData = newTrainingData
        cubes = newCubes
        results = ExperimentResultSingletonHolder.getInstance()
        assert results != null, "Could not get an ExperimentResult instance in ClassData"
    }

    /**
     *  Checks whether the cube.ClassData is conflicting, and if yes, resolve it according to the strategy set in
     *  CONFLICT_RESOLUTION. To tread a collection of cubes, this method should be called for each cube.
     * @param cube .. whose conflict we resolve
     * @param it Iterator to cube collection pointing to the current cube - used to manipulate the collection for strategy "d"
     */
    void resolveConflict(ClassCube cube, ListIterator<ClassCube> it) {

        if (!cube.classData.hasConflict)
            return

        def pConf = (String) results.Pconf
        final int ek = results.getInt("Ek") // #parts into which training set has been split (= #parts to be merged)
        switch (pConf) {

            case "c":   // ConflictResolutionType.MAJORITY_CLASS
            case "d":   // ConflictResolutionType.RECURSIVE_SUBDIVISION

                def instancesInCube = filterInstancesByCube(trainingData, cube, ek)
                def numInstances = instancesInCube.numInstances()

                def numClasses = trainingData.numClasses()

                if (numInstances > 0) {
                    double[] newProbs = new double[numClasses]

                    // calculate class frequencies
                    for (int i = 0; i < numInstances; i++) {

                        int classIndex = instancesInCube.instance(i).classValue()
                        newProbs[classIndex] += 1
                    }

                    Utils.normalize(newProbs, numInstances)

                    cube.setClassProbDistribution(newProbs)
                    cube.classData.setClassToValueWithHighestProbabilityInDistribution()

                    // double maxFreq = newProbs[Utils.maxIndex(newProbs)]

                    // ConflictResolutionType.RECURSIVE_SUBDIVISION
                    // TODO: Parameters
                    if ((pConf == "d") && (numInstances > 100)) {  // (ii)
                        it.remove()     // remove current cube from collection, will be replaced by "sub-cubes"
                        int cubesAdded = -1
                        def classifier = new MergedTreeClassifier()
                        def result = classifier.buildClassifierInternal(instancesInCube, false, 1)
                        for (innerCubeUncut in result) {

                            ClassCube innerCubeCut = innerCubeUncut.intersectBounds((Cube) cube)
                            if (innerCubeCut != null) {
                                it.add(innerCubeCut)
                                cubesAdded += 1
                            }
                        }
                        assert cubesAdded >= 0
                    } // else: nothing to do, as ConflictResolutionType.RECURSIVE_SUBDIVISION (i) is same as ConflictResolutionType.MAJORITY_CLASS
                } else { // else: No instances in Cube, nothing to do
                    // TODO: results.instancelessConflictCubes = (results.instancelessConflictCubes as int) + 1
                }
                break


            case "b":   // ConflictResolutionType.MERGE_DISTRIBUTIONS
                // We assume here that the class and distrib. are as after the call of mergeClassData() - i.e.
                // class is set by highest confidence and prob. distributions are averaged
                cube.classData.setClassToValueWithHighestProbabilityInDistribution()
                break
            case "a":   // ConflictResolutionType.HIGHEST_CONFIDENCE_CLASS
                // nothing to do, class already computed during mergeClassData()
                break
            default:
                throw new IllegalArgumentException("Wrong option for parameter value Pconf specified: $pConf")
        }

        // all ok, clean up the conflict
        cube.classData.clearConflict()
    }

    // Filters trainingData (Instances) returning only those inside of a cube
    static Instances filterInstancesByCube(Instances trainingData, ClassCube cube, int numPartsOfTrainingData) {
        def filter = new RemoveCube()
        filter.setInputFormat(trainingData)
        filter.setCube(cube)
        filter.setInvertSelection(true)

        Instances filtered
        if (trainingData) {
            filtered = useFilter(trainingData, filter)
        } else {

            filtered = useFilter(loadArff("split0"), filter)
            for (int i = 1; i < numPartsOfTrainingData; i++) {

                def f = useFilter(loadArff("split${i}"), filter)
                for (int j = 0; j < f.numInstances(); j++) {
                    filtered.add(f.instance(j))
                }
            }
        }
        return filtered
    }

}

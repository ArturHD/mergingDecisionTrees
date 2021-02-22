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
import cubes.BuildTreeAndGetBoxSet
import edu.pvs.batchrunner.ExperimentResult
import weka.core.converters.ConverterUtils.DataSource
import static BuildTreeAndGetBoxSet.TreeType.RandomForest
import cubes.BoxSet

/**
 * Merging of decision trees built from different arff files.
 * Usage: J48MergingA <class-index> <ARFF-file1> <ARFF-file2> ...
 *      where class-index is index of the class attribute within the ARFF files (all must have same attributes)
 */

if (args.size() < 3) {
    println "Usage: MergingA <class-index> <ARFF-file1> <ARFF-file2> ..."
    println "       where class-index is index of the class attribute within the ARFF files (all files must have same attributes)"
    System.exit(0)
}

int classIndex = (args[0]).toInteger()
int numModels = args.size() - 1

// 1. Read ARFF files and Create cubes
List<List<ClassCube>> cubeCollections = []

for (int fileIndex in 1..numModels) {
    def fileName = args[fileIndex]

    // load data and set class index
    println "Reading file #$fileIndex ($fileName)"
    def data = DataSource.read(fileName)
    data.setClassIndex(classIndex)

    // create and save the model
    println "Training model for file #$fileIndex (on ${data.numAttributes()} attributes)"
    ccb = new BuildTreeAndGetBoxSet(new ExperimentResult().treeType = RandomForest)
    def cubesFromSingleModel = ccb.buildCubes(data)
    println "Model for file #$fileIndex yielded ${cubesFromSingleModel.size()} cubes"
    cubeCollections << cubesFromSingleModel
}

// 2. Compute intersections - find intersections from each model
def container = new BoxSet()
def fileIndex = 1
for (col in cubeCollections) {
    println "Adding cube collection to cube container for file #$fileIndex"
    container.mergeBoxSetsViaIntersections col
    println "Operation yielded ${container.size()} cubes"
    //container.class2geomMap.keySet().each {println it}
    fileIndex++
}

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
import weka.classifiers.trees.j48.J48toCubes
import weka.core.Instances
import weka.classifiers.trees.*
import experiment.ExperimentResultSingletonHolder
import experiment.Tools

/**
 * Instantiates a Weka-tree-classifier (type: see TreeType) and creates a collection of boxes from it.
 */

// @Typed @Canonical
@Typed
class BuildTreeAndGetBoxSet {

    static enum TreeType {
        J48 /* default */,
        RandomForest
    }

    private ExperimentResult results

    // Default constructor initializing the experiment.
    BuildTreeAndGetBoxSet(ExperimentResult results = null) {

        if (!results)
            results = ExperimentResultSingletonHolder.getInstance()
        // assert results != null, "Invalid ExperimentResult supplied or could not retrieve from ExperimentResultSingletonHolder"
        this.results = results
    }

    BoxSet buildCubes(Instances instances) {

        // def treeType = (results && results.Etf) ? TreeType.valueOf(results.Etf) : TreeType.J48
        def treeType = (results && results.Etf) ? results.getEnum("Etf",  TreeType.class) : TreeType.J48
        //def treeType = TreeType.valueOf(results.Etf)

        BoxSet boxSet = null
        switch (treeType) {

            case TreeType.J48:

                if (results && results.classifierParameters) {
                    // todo: handle J48 params - e.g. pruning level
                }
                def newModel = new J48()
                //newModel.binarySplits = true
                newModel.buildClassifier(instances)
                boxSet = J48toCubes.treeToBoxes(newModel)
                // Add the bounding box computed from training instances
                boxSet.boundingBox = Tools.getBoundingBox(instances)
                break
            case TreeType.RandomForest:

                if (results && results.classifierParameters) {
                    // todo: handle RandomForest params - e.g. pruning level
                }
                def newModel = new RandomForest()

                newModel.buildClassifier(instances)

                GetAccessToClassifiersInRandomForest.getClassifiers(newModel).each {
                    RandomTree rt = (RandomTree) it
                    BoxSet treeBoxes = RandomTreeToBoxes.treeToBoxes(rt)
                    boxSet.mergeBoxSetsViaIntersections(treeBoxes)
                }
                // Add the bounding box computed from training instances
                boxSet.boundingBox = Tools.getBoundingBox(instances)
                break

            default:
                assert false, "TreeType (parameter Etf) is unknown: $treeType"
        }
        return boxSet
    }
}

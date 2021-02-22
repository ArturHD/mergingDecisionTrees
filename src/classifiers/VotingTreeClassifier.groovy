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
import edu.pvs.batchrunner.ExperimentResult
import groovy.util.logging.Log
import trees.TreeFromBoxesBuilder

import weka.classifiers.Classifier
import weka.core.Attribute
import weka.core.Instance
import weka.core.Instances
import weka.filters.Filter
import static experiment.Tools.loadArff

import cubes.BuildTreeAndGetBoxSet

import static experiment.Tools.createKSplits
import experiment.PerfUtils
import experiment.ExperimentResultSingletonHolder
import trees.TreeOfBoxesNode
import cubes.BoxSet
import experiment.Tools

/**
 * Created by: flangner 
 * Date: 08.02.12
 * Time: 15:41
 * This class implements a voting classifier, which builds several trees (for each data part)
 * and classifies by merging the distribution vectors
 * todo: check - why aren't the J48 classifiers used directly, without creating box sets, and then trees on them (as here)?
 */
@Log @Typed
class VotingTreeClassifier extends Classifier implements PerfUtils {

    // Roots of the classification trees
    private TreeOfBoxesNode[] roots

    // Number of parts into which data is partitioned
    private Integer k = 1

    /**
     * Triggers building of the tree from a collection of boxes
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data
     */
    @Override
    void buildClassifier(Instances data) {

        // classifier is build from the given data
        if (roots == null) {

            ExperimentResult experimentResult = ExperimentResultSingletonHolder.getInstance()
            // assert experimentResult != null, "Could not retrieve ExperimentResult from ExperimentResultSingletonHolder"
            this.k = (experimentResult) ? experimentResult.getInt("Ek") : 1
            int discretize = (experimentResult) ? experimentResult.getInt ("Pdisc") : 2

            roots = new TreeOfBoxesNode[k]

            def t0 = tic()
            List splits = createKSplits(k, data, experimentResult)
            def t1 = tic()

            def boxSetBuilder = new BuildTreeAndGetBoxSet(experimentResult)

            for (int i = 0; i < k; i++) {

                Instances split = (splits) ? splits.get(i) : loadArff("split${i}")

                if (discretize > 0) {
                    def supervised = discretize == 2
                    split = Tools.discretizeInstances(split, supervised)
/*
                    // Discretize data
                    def filter = (discretize == 1) ? new weka.filters.unsupervised.attribute.Discretize() : new weka.filters.supervised.attribute.Discretize()
                    filter.setInputFormat(split)
                    split = Filter.useFilter(split, filter)
*/
                }

                // Create a box set by building a tree-classifier (weka) and turning into boxes
                def cubes = boxSetBuilder.buildCubes(split)
                ((List<Integer>) experimentResult.cubesBuilt) << cubes.size()

                // todo: AA: I think this is not needed here, or do we need the statistics for voting classifier?
                // If experimentResult.Pmerg == 0, join adjacent boxes after *final* merge to reduce # of boxes
                if (experimentResult && experimentResult.getInt("Pmerg") == 1) {
                    def oldCubeCount = cubes.size()
                    def joiner = new JoinAdjacentCubes(cubes)

                    BoxSet joinedCubes = joiner.joinAdjacentCubes()
                    def treeBuilder = TreeFromBoxesBuilder.create(joinedCubes)
                    treeBuilder.buildTree()
                    roots[i] = treeBuilder.getTree()

                    experimentResult.mergedBoxesCount = (experimentResult.mergedBoxesCount as int) + joiner.totalJoinedCubes
                    (experimentResult.cubeCompressionRatio as List<Integer>) << (1 - joinedCubes.size() / oldCubeCount) * 100.0
                } else {
                    def treeBuilder = TreeFromBoxesBuilder.create(cubes)
                    treeBuilder.buildTree()
                    roots[i] = treeBuilder.getTree()
                }

                // roots[i].buildTree()
            }
            def t2 = tic()

            classifiers.VotingTreeClassifier.log.info("TC: Splitting took ${toDiffString(t0, t1)} and building of all the $k trees took ${toDiffString(t1, t2)}")
        }
    }

    /**
     * Predicts the class memberships for a given instance. I
     * This is done by averaging the distributions of all k trees to a single distribution.
     * todo: this makes sense only for voting on normal trees; check, that MTC does not use this
     */
    @Override
    public double[] distributionForInstance(Instance instance) throws Exception {

        double[] resultDistribution = new double[instance.numClasses()];

        switch (instance.classAttribute().type()) {

            case Attribute.NOMINAL:
                // todo: check - is this without class attribute (should be w/out)?
                double[] attributeVector = instance.toDoubleArray()
                final double OneOverK = (1.0 / k)
                for (int i = 0; i < k; i++) {

                    double[] distributionOfOneTree = roots[i].distributionForAttributeVector(attributeVector)
                    assert distributionOfOneTree.length == instance.numClasses(), "Distribution vector from tree has different length (${distributionOfOneTree.length}) than supplied Instance (${resultDistribution.length})"

                    // average the distributions of all k trees to a single distribution which becomes result
                    for (int j = 0; j < resultDistribution.length; j++) {
                        resultDistribution[j] += OneOverK * distributionOfOneTree[j]
                    }
                }
                break
            case Attribute.NUMERIC:
                throw new InternalError("VotingTreeClassifier does not support classification for numeric classes.")
        }

        return resultDistribution
    }

    @Override
    public String toString() {

        def result = new StringBuilder()
        roots.each { root -> result.append(root).append("\n")}
        return result.toString()
    }
}
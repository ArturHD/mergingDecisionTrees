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

import groovy.transform.Canonical
import groovy.util.logging.Log
import weka.core.Instances
import weka.filters.Filter
import weka.filters.unsupervised.instance.RemoveWithValues

/**
 * Encapsulates different strategies for pruning a tree of boxes
 * User: artur
 * Date: 21.03.12
 * Time: 16:13
 */
@Typed @Log
class Pruning {

    TreeOfBoxesNode tree
    Instances pruningData
    boolean pruneUntestableSubtrees
    double maxLeafOverTreeErrorFraction = 0.4
    int minTestInstancesToCollapseBranch = 5
    int maxLeaves = -1

    Pruning(TreeOfBoxesNode tree, Instances pruningData, boolean pruneUntestableSubtrees) {
        this.tree = tree
        this.pruningData = pruningData
        this.pruneUntestableSubtrees = pruneUntestableSubtrees
    }

    void prune() {
        def pruningTree = new PruningNode(tree, pruningData, this)
        int oldNumLeaves = pruningTree.buildTree()
        int numLeaves = pruningTree.prune()
//        while ((maxLeaves > 0) && (numLeaves > maxLeaves)) {
//            maxLeafOverTreeErrorFraction -= 0.1
//            minTestInstancesToCollapseBranch--
//            log.info("RePruning with maxLeafOverTreeErrorFraction=$maxLeafOverTreeErrorFraction, current number of leaves=$numLeaves")
//            numLeaves = pruningTree.prune()
//        }
        log.info("Pruning finished with $numLeaves leaves: ${oldNumLeaves - numLeaves} leaves pruned.")
    }
}

@Typed @Canonical @Log
class PruningNode {
    TreeOfBoxesNode node
    Instances pruningData
    Pruning pruning
    List<PruningNode> children

    int buildTree() {
        if (!isLeaf()) {
            children = new ArrayList<PruningNode>(2)
            def splitDim = node.model.splitDim
            def splitPoint = node.model.splitPoint
            def childPruningData = splitTestInstances(pruningData, splitPoint, splitDim)
            int numLeaves = 0
            for (int i in 0..<node.children.size()) {
                def child = new PruningNode(node.children.get(i), childPruningData.get(i), pruning)
                numLeaves += child.buildTree()
                children << child
            }
            return numLeaves
        } else
            return 1
    }

    boolean isLeaf() {
        node.isLeaf()
    }

    void toLeaf() {
        node.toLeaf()
        children = null
    }

    /**
     * Prunes this subtree by replacing a branch by a leaf (if errors on the leaf <= errors on tree)
     * See weka.classifiers.trees.j48.PruneableClassifierTree.prune
     * @return the number of leaves in the pruned subtree
     */
    int prune() {
        if (isLeaf())
            return 1

        int numLeavesInSubtrees = 0
        for (PruningNode child in children) {
            numLeavesInSubtrees += child.prune()
        }

        // if the error using a leaf <= error using subtree, then collapse the subtree
        def errorsLeaf = errorsForLeaf()
        def errorsTree = errorsForTree()
        // if this is negative or not larger maxLeafOverTreeErrorFraction, then prune
        double leafOverTreeErrorRatio = (errorsLeaf - errorsTree) / errorsTree
        // if (Utils.sm(errorsLeaf, errorsTree)) {
        if (leafOverTreeErrorRatio <= pruning.maxLeafOverTreeErrorFraction) {
            log.info("Pruning: errorsLeaf, errorsTree = $errorsLeaf, $errorsTree; node = ${this.node}")
            toLeaf()
            return 1
        } else {
            return numLeavesInSubtrees
        }
    }

    double errorsForTree() {
        if (isLeaf()) {
            return errorsForLeaf()
        } else {
            if (pruningData == null || pruningData.numInstances() <= pruning.minTestInstancesToCollapseBranch) {
                if (pruning.pruneUntestableSubtrees)
                    return Double.POSITIVE_INFINITY   // prune this subtree
//                else
//                    return 0.0 // C45PruneableClassifier: No test -> No error, i.e. most likely no pruning of this subtree
            }
            // if we have more then minTestInstancesToCollapseBranch test instances, true tree error is computed here
            double result = 0.0
            for (it in children) {
                result += it.errorsForTree()
            }
            return result
        }
    }

    double errorsForLeaf() {
        if (pruningData == null || pruningData.numInstances() <= pruning.minTestInstancesToCollapseBranch) {
            if (pruning.pruneUntestableSubtrees)
                return Double.POSITIVE_INFINITY   // prune this leaf
//            else
//                return 0.0 // C45PruneableClassifier: No test -> No error, i.e. most likely no pruning of this leaf
        }
        double total = pruningData.numInstances()
        double correctlyPredicted = 0.0
        for (int i in 0..<total) {
            if (pruningData.instance(i).classValue() == node.model.classData.classValue)
                correctlyPredicted++
        }

        (total - correctlyPredicted)
    }

    protected List<Instances> splitTestInstances(Instances pruningData, double splitPoint, int splitDim) {
        Instances lowerPruningData = null
        Instances upperPruningData = null

        if (pruningData?.numInstances() > 0) {
            Filter testFilterLower = createTestFilter(pruningData, splitPoint, splitDim, true)
            lowerPruningData = Filter.useFilter(pruningData, testFilterLower)
            if (lowerPruningData.numInstances() == 0)
                lowerPruningData = null

            Filter testFilterUpper = createTestFilter(pruningData, splitPoint, splitDim, false)
            upperPruningData = Filter.useFilter(pruningData, testFilterUpper)
            if (upperPruningData.numInstances() == 0)
                upperPruningData = null
        }
        return [lowerPruningData, upperPruningData]
    }

    private Filter createTestFilter(Instances data, double splitPoint, int splitDim, boolean invertSelection) {
        def testFilter = new RemoveWithValues()
        testFilter.with {
            setAttributeIndex((splitDim + 1).toString())
            setSplitPoint(splitPoint)
            setInputFormat(data)
            setInvertSelection(invertSelection)
        }
        return testFilter
    }

}

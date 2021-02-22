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

import experiment.Tools
import groovy.util.logging.Log
import weka.core.Instances
import cubes.BoxSet
import cubes.Cube
import experiment.PerfUtils
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder

/**
 * Top-level class for different pruning and tree building strategies
 * todo: describe briefly differences of UnlimitedTreeFromBoxesBuilder, MaxLeafsTreeFromBoxesBuilder, MaxDepthTreeFromBoxesBuilder
 * User: flangner && AA
 * Date: 28.02.12 && 16.06.2012
 */
@Log @Typed
abstract class TreeFromBoxesBuilder {

    private final BoxSet boxes

    private final Cube boundingBox

    private TreeOfBoxesNode root

    // true, if pruning data has been supplied
    private final boolean isPruningDataAvail

    // Should cubes for which there are no samples in the pruning data be pruned?
    private final boolean pruneUntestableLeaves

    private final Instances pruningData

    // Type of pruning
    private final int pruningParam

    // abstract constructor to initialize the builder - only used internally
    protected TreeFromBoxesBuilder(BoxSet _boxSet, Instances _pruningData, int _pruningParam) {
        this.boxes = _boxSet
        this.boundingBox = _boxSet.boundingBox
        this.pruneUntestableLeaves = (_pruningParam == 2)
        this.pruningData = _pruningData
        this.pruningParam = _pruningParam

        root = new TreeOfBoxesNode(_boxSet)
        isPruningDataAvail = (_pruningData != null)
        NodeModel.MAX_DEPTH = Integer.MAX_VALUE
        NodeModel.MAX_CUBES_FOR_LEAF = 1

        // log.info("""${this.class.getSimpleName()} building tree from ${boxes.size()} cubes (hash ${Tools.getHashSum(boxes)}).""")
    }

    // method to actually build the tree
    abstract void buildTree();

    // returns the result of the tree-building
    TreeOfBoxesNode getTree() {
        return root
    }

    // returns a BoxSet with the boxes of the root and the *original* boundingBox
    BoxSet getBoxSet() {
        BoxSet result = new BoxSet(root.getBoxSet())
        result.boundingBox = this.boundingBox
        return result
    }

    // A static "constructor" method
    // todo: fix the parameter passing and calling the abstract constructor (because we get different parameters)
    static TreeFromBoxesBuilder create(BoxSet boxSet, Instances pruningData = null, int pruningParam = 0, int maxDepth = 0, int maxLeafs = 0) {

        if (pruningParam <= 9) {
            // tree building with classical pruning
            if (maxDepth > 0) {
                return new MaxDepthTreeFromBoxesBuilder(boxSet, pruningData, pruningParam, maxDepth)
            } else if (maxLeafs > 0) {
                return new MaxLeafsTreeFromBoxesBuilder(boxSet, pruningData, pruningParam, maxLeafs)
            } else {
                return new UnlimitedTreeFromBoxesBuilder(boxSet, pruningData, pruningParam)
            }
        } else {
            // tree building with RankedBoxes Pruning (June 2012)
            return new RankedPruningTreeFromBoxesBuilder(boxSet, pruningData, pruningParam)
        }
    }


    /*
    Builds a tree from boxes where pruning is implemented as filtering of less important boxes before tree growing.
    This implies that boxes do not cover the space fully, and so "terminal superboxes" are used as the leafs.
     */
    @Typed
    final static class RankedPruningTreeFromBoxesBuilder extends TreeFromBoxesBuilder implements PerfUtils {


        RankedPruningTreeFromBoxesBuilder(BoxSet _boxSet, Instances _pruningData, int  _pruningParam) {
            super(_boxSet, _pruningData, _pruningParam)
            // this.isPruningDataAvail = true // disregard that we might have no pruningData - still prune
        }

        void buildTree() {

            def t0 = tic()
            def numBoxes = boxes.size()

            // first, filter out cubes not important boxes
            def boxFilter = new RankedBoxesPruning(boxes, pruningParam)
            def reducedBoxSet = boxFilter.filterBoxes()

            def t1 = tic()

            appendPerfLogEntry(ExperimentResultSingletonHolder.getInstance(), "PruneBox", numBoxes, -1L, reducedBoxSet.size(), -1L, timeDiff(t0,t1), memDiff(t0,t1))


            // re-create the root
            root = new TreeOfBoxesNode(reducedBoxSet)
            tree.buildTree()

            def t2 = tic()

            appendPerfLogEntry(ExperimentResultSingletonHolder.getInstance(), "TreeBuild", reducedBoxSet.size(), -1L, reducedBoxSet.size(), -1L, timeDiff(t1,t2), memDiff(t1,t2))

        }
    }

    /**
     * The algorithm covered by this implementation will build a tree with as many leafs as the cube collection contains
     * cubes. In other words each cube from the collection will become a leaf within the tree without further
     * limitations.
     */
    @Typed
    final static class UnlimitedTreeFromBoxesBuilder extends TreeFromBoxesBuilder {

        UnlimitedTreeFromBoxesBuilder(BoxSet _boxSet, Instances _pruningData, int  _pruningParam) {
            super(_boxSet, _pruningData, _pruningParam)

            NodeModel.MAX_DEPTH = Integer.MAX_VALUE
            NodeModel.MAX_CUBES_FOR_LEAF = 1
        }

        void buildTree() {
            def tree = getTree()
            tree.buildTree()
            if (isPruningDataAvail) {
                Pruning pruning = new Pruning(tree, pruningData, pruneUntestableLeaves)
                pruning.prune()
            }
        }
    }

    /**
     * This class contains an algorithm to build a tree from boxes that has a limited depth. More precisely the
     * recursive descent will continue until either the maximal allowed depth is reached or a node can not be split any
     * further because it contains less than two cubes.
     */
    @Typed
    final static class MaxDepthTreeFromBoxesBuilder extends TreeFromBoxesBuilder {

        MaxDepthTreeFromBoxesBuilder(BoxSet _boxSet, Instances _pruningData, int  _pruningParam, int _maxDepth) {
            super(_boxSet, _pruningData, _pruningParam)

            NodeModel.MAX_DEPTH = _maxDepth
            NodeModel.MAX_CUBES_FOR_LEAF = 1
        }

        void buildTree() {
            def tree = getTree()
            tree.buildTree()
            if (isPruningDataAvail) {
                Pruning pruning = new Pruning(tree, pruningData, pruneUntestableLeaves)
                pruning.prune()
            }
        }
    }

    /**
     * In this variant the amount of leafs for a tree is limited. As long as the the limit is not violated and there
     * are leafs with more than one cube left one of those leafs will be split up into two new leafs. To determine which
     * leaf has to be split up next, the available leafs are ranked by their highest class probability. If the highest
     * class probability is low for a leaf it most likely needs to be split to get a valid decision tree.
     */
    @Typed
    final static class MaxLeafsTreeFromBoxesBuilder extends TreeFromBoxesBuilder {


        private final static Comparator<TreeOfBoxesNode> TREE_NODE_COMPARATOR = new Comparator<TreeOfBoxesNode>() {

            int compare(TreeOfBoxesNode node1, TreeOfBoxesNode node2) {

                return Double.compare(node1.model.highestClassProbability(), node2.model.highestClassProbability())
            }
        }

        private final PriorityQueue<TreeOfBoxesNode> nodesToProcess
        private final int maxLeafs

        MaxLeafsTreeFromBoxesBuilder(BoxSet _boxSet, Instances _pruningData, int  _pruningParam, int _maxLeafs) {
            super(_boxSet, _pruningData, _pruningParam)

            this.maxLeafs = _maxLeafs
            NodeModel.MAX_DEPTH = Integer.MAX_VALUE
            NodeModel.MAX_CUBES_FOR_LEAF = 1
            TreeOfBoxesNode.treeStatistics = new TreeOfBoxesNode.TreeStatistics()

            // initialize the priority queue
            // (with default initial capacity @see java.util.PriorityQueue.DEFAULT_INITIAL_CAPACITY)
            nodesToProcess = new PriorityQueue<TreeOfBoxesNode>(11, TREE_NODE_COMPARATOR)

            nodesToProcess.add(getTree())
        }

        void buildTree() {

            // build inner nodes
            int numLeafs = nodesToProcess.size()
            TreeOfBoxesNode currentNode
            while (0 < nodesToProcess.size() && numLeafs < maxLeafs) {

                currentNode = nodesToProcess.poll()
                numLeafs--

                // handle inner nodes
                if (currentNode.boxes.size() > NodeModel.MAX_CUBES_FOR_LEAF &&
                        currentNode.model.highestClassProbability() < 1.0) {

                    // do the splitting
                    List<BoxSet> segregationResult = currentNode.model.computeSplit()
                    TreeOfBoxesNode.treeStatistics.updateTotalNumBoxesCut currentNode.model.numBoxesCut

                    // and set children
                    for (childContent in segregationResult) {

                        def child = new TreeOfBoxesNode(childContent)
                        child.father = currentNode
                        currentNode.children << child
                        nodesToProcess.add(child)
                        numLeafs++
                    }

                    // forget cube collection
                    currentNode.boxes = null
                    currentNode.isComputed = true

                    // handle leafs
                } else {

                    currentNode.toLeaf()
                    numLeafs++
                }
            }

            // make the remaining nodes to leafs
            for (TreeOfBoxesNode node: nodesToProcess) {
                node.toLeaf()
            }
        }
    }
}

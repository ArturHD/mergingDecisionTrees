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
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import groovy.util.logging.Log
import weka.core.Instances
import cubes.BoxSet

/**
 * Created by IntelliJ IDEA.
 * User: Artur Andrzejak
 * Date: 02.02.12
 * Time: 15:57
 * Stores a node of a decision tree on a collection of cubes (boxes)
 * Various tree building methods are planned:
 * A. Get dim and splitpoint which cuts least number of boxes
 */
@Typed @Log
class TreeOfBoxesNode {

    // Collection of cubes at this node
    protected Collection<ClassCube> boxes

    protected int numCubes = -1

    protected Instances pruningData

    protected boolean pruneUntestableSubtrees

    // Info about class at a leaf, cutpoints etc. for inner nodes
    protected NodeModel model

    // Children nodes; init with obj to avoid NullPointerException
    protected List<TreeOfBoxesNode> children = new ArrayList<TreeOfBoxesNode>()

    protected TreeOfBoxesNode father

    protected boolean isComputed = false

    // Just for convenience
    protected int nDims = -1

    // todo: is there sth better than static but not nasty?
    static TreeStatistics treeStatistics

    protected ExperimentResult experimentResult
    int treeDepth

    // Or better a Static Factory Method?  see Effective Java #1, http://goo.gl/hpiru
    TreeOfBoxesNode(Collection<ClassCube> _boxSet) {
        assert _boxSet && _boxSet.size() > 0

        this.boxes = _boxSet
        this.numCubes = _boxSet.size()

        this.model = new NodeModel(this)
        if (boxes.size() > 0) {
            nDims = getFirstCube().nDims
        }
        experimentResult = ExperimentResultSingletonHolder.getInstance()
    }

    boolean isLesserChild(TreeOfBoxesNode node) {
        assert children.size() > 1

        children[0] == node
    }

    ClassCube getFirstCube() {
        if (boxes == null || boxes.size() == 0)
            return null
        return boxes.iterator().next()
    }

    // Collapses this tree to a leaf
    void toLeaf() {
        model.setSuperCube()
        this.numCubes = 1
        children.clear()    // just to be sure
        isComputed = true
    }

    boolean isLeaf() {
        // assert isComputed, "Trying to check for leaf before computing the NodeModel"
        if (children == null || children?.size() == 0)
            return true
        return false
    }

    /**
     *  Computes the split, model and possibly children
     * @return
     */
    protected split() {
        // 1. Test, if we are a leaf
        def isLeaf = model.isLeafDecision()
        if (isLeaf) {
            toLeaf()
            return
        }

        // 2. not a leaf, do the splitting
        List<List<ClassCube>> segregationResult = model.computeSplit()
        treeStatistics.updateTotalNumBoxesCut model.numBoxesCut

        // and set children
        for (childContent in segregationResult) {
            def child = new TreeOfBoxesNode(childContent)
            child.father = this
            children << child
        }

        // forget cube collection
        boxes = null

        isComputed = true
    }

    // Return all boxes of this subtree as a BoxSet
    // Carefully: results is possibly WITHOUT the bounding box!
    BoxSet getBoxSet() {
        assert isComputed, "Attempting to invoke getBoxSet() before tree is build - call buildTree() first."
        if (isLeaf()) {
            return new BoxSet(boxes)
        } else {
            BoxSet result = new BoxSet()
            for (TreeOfBoxesNode child in children) {
                result.addAll(child.getBoxSet())
            }
            return result
        }
    }

    /** Builds tree recursively
     * @return
     */
    def buildTree(int recursionDepth = 0) {
        treeDepth = recursionDepth
        if (!isComputed) {

            if (recursionDepth == 0) {
                treeStatistics = new TreeStatistics()
            } else {
                treeStatistics.updateMaxRecursionDepth recursionDepth
            }

            // def time = System.currentTimeMillis()

            model.setClass()
            this.split()

/*            if (recursionDepth < 3) {
                System.out.println("TreeBuilding: Computing a node of depth " + recursionDepth + " took " +
                        (System.currentTimeMillis() - time) + "ms.")
            }
  */
        }
        // recursive descend
        for (TreeOfBoxesNode child in children) {
            child.buildTree(recursionDepth + 1)
        }
    }

    /**
     * Performs the classification by descending the tree. Returns the class prob. distribution stored in the final node
     * @param attributeVector
     * @return
     */
    double[] distributionForAttributeVector(double[] attributeVector) {
        assert isComputed, "Attempting to classify before tree is build - call buildTree() first."
        assert (attributeVector.size() - nDims) <= 1, "The supplied attributeVector has ${attributeVector.size()} dimensions, tree data has $nDims."

        ClassCube leafCube = findCubeForAttributeVector(attributeVector)
        return leafCube.classData.classProbDistribution
    }

    /**
     * Descends recursively the tree according to attributeVector values until the leaf cube
     * @param attributeVector
     * @return
     */
    protected ClassCube findCubeForAttributeVector(double[] attributeVector) {
        if (this.isLeaf())
            return this.getFirstCube()

        double splitPoint = model.splitPoint
        assert !(Double.isNaN(splitPoint))
        double attributeValue = attributeVector[model.splitDim]
        int nextNodeIndex = attributeValue <= splitPoint ? 0 : 1
        def nextNode = this.children[nextNodeIndex]
        def result = nextNode.findCubeForAttributeVector(attributeVector)
        return result
    }

    private recursiveToString(int level, StringBuilder sb) {
        if (this.isLeaf())
            return
        children.each() {
            it.appendOwnInfo(level + 1, sb)
        }
        // recursive descend
        for (child in children)
            child.recursiveToString(level + 1, sb)
    }

    private appendOwnInfo(int level, StringBuilder sb) {
        // sb.append("Level# $level, $model, #cubes = ${cubes.size()}, hash children=${children?.collect{it.hashCode()}}")
        sb.append("Level# $level, $model, #cubes = $numCubes, hash ${this.hashCode()}, children=${children?.collect {it.hashCode()}}\n")
    }

    @Override
    public String toString() {
        def result = new StringBuilder(100)
        result.append("TreeOfBoxesNode from collection of size = $numCubes\n")

        appendOwnInfo(0, result)
        recursiveToString(0, result)
        return result.toString()
    }

    static class TreeStatistics {
        int maxRecursionDepth = 0
        int totalNumBoxesCut = 0

        def updateMaxRecursionDepth(int newDepth) {
            maxRecursionDepth = newDepth > maxRecursionDepth ? newDepth : maxRecursionDepth
        }

        def updateTotalNumBoxesCut(int numBoxesCut) {
            totalNumBoxesCut += numBoxesCut
        }
    }
}

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

import com.google.common.collect.Iterables
import cubes.ClassCube
import cubes.ClassData
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import groovy.util.logging.Log

/**
 * Created by IntelliJ IDEA.
 * User: Artur Andrzejak 
 * Date: 02.02.12
 * Time: 16:31
 * Contains information about cutpoints etc for inner nodes; and about classes + distributions for leaves
 * Later will use various approaches (=> subclasses?)
 */
@Typed @Log
class NodeModel implements PerfUtils {

    // ########## SETTINGS #########
    // Max number of cubes in a leaf, important for deciding whether leaf or not
    static Integer MAX_CUBES_FOR_LEAF = 1
    static Integer MAX_DEPTH = Integer.MAX_VALUE

    // ########## Instance data ########
    // For class of leaves
    ClassData classData

    // The selected splitPoint
    double splitPoint = Double.NaN

    // The dimension of the splitPoint
    int splitDim = -1

    // Number of boxes cut at the split (is set by computeSplit())
    int numBoxesCut

    protected TreeOfBoxesNode node

    protected SplitOptimizer optimizer

    protected int nDims

    protected int numClasses

    protected ExperimentResult experimentResult

    private double cachedHighestClassProbability = -1.0

    NodeModel(TreeOfBoxesNode node) {

        assert node

        this.node = node
        // assert cubes.size() > 0, "Created NodeModel with 0 boxes in the collection"

        this.experimentResult = ExperimentResultSingletonHolder.getInstance()

        ClassCube aCube = getFirstCube()
        if (aCube != null) {
            nDims = aCube.nDims

            numClasses = aCube.classData?.classProbDistribution?.length
            def int numBags = 2   // we have a binary tree => two bags (for below and above splitpoint)

            // Set type of splitpoint search from experimentResult
            def sst = SplitOptimizer.SplitSearchType.MIN_NUM_SPLITS
            if (experimentResult != null)
                sst = SplitOptimizer.SplitSearchType.valueOf((String) experimentResult.Pgrow)
            optimizer = SplitOptimizer.create(sst, numBags, numClasses)
        }
    }

    ClassCube getFirstCube() {

        if (node.boxes == null || node.boxes.size() == 0)
            return null

        return Iterables.get(node.boxes, 0)
    }

    /**
     * @return the highest class probability for the cubes connected to this node.
     */
    double highestClassProbability() {

        if (cachedHighestClassProbability < 0.0) {

            // todo implement meaningful strategy
            final double weight = 1.0

            if (node.boxes == null || node.boxes.size() == 0)
                return 1.0

            double[] probabilitySums = new double[node.boxes.iterator().next().classData.classProbDistribution.length]

            double highestProbability = 0.0
            for (ClassCube cube in node.boxes) {
                for (int i = 0; i < probabilitySums.length; i++) {

                    double newProbability = probabilitySums[i] + weight * cube.classData.classProbDistribution[i]
                    probabilitySums[i] = newProbability
                    highestProbability = (newProbability > highestProbability) ? newProbability : highestProbability
                }
            }

            final double scale = (double) (1.0 / (double) node.boxes.size())

            cachedHighestClassProbability = scale * highestProbability
        }

        return cachedHighestClassProbability
    }

    boolean isLeafDecision() {
        boolean result = node.boxes.size() <= MAX_CUBES_FOR_LEAF || node.treeDepth >= MAX_DEPTH
        if (node.pruneUntestableSubtrees)
            result |= (node.pruningData == null) || (node.pruningData.numInstances() == 0)
        return result
    }

    /** Compute and set the class from classData of all boxes in this superbox (i.e. node) */
    def setClass() {
        // if only one cube, this is easy
        if (node.boxes.size() == 1) {
            ClassCube cube = node.getFirstCube()
            this.classData = (ClassData) cube.classData.clone()

        } else {
            // Multiple cubes, average the probabilityDistributionVector from all cubes
            def numClasses = node.getFirstCube().classData.classProbDistribution.size()
            def result = new double[numClasses]
            double OneoverK = 1.0 / (double) node.boxes.size()
            for (ClassCube cube in node.boxes) {
                // add all components of probabilityDistributionVector of this supercube to result
                for (int j = 0; j < result.length; j++) {

                    result[j] += cube.classData.classProbDistribution[j] * OneoverK
                }
            }
            this.classData = new ClassData()
            classData.setClassProbDistribution(result)
            classData.setClassToValueWithHighestProbabilityInDistribution()
        }
    }

    /** Compute and set the cube to the cube filling the space bounded by the tree path */
    def setSuperCube() {
        // if we have only 1 cube in this node, make it fast and return immediately
        //      AA 17.06.2012 - disabled due to compensate for non-complete space covering in RankedBoxesPruning
        //      if (this.node.cubes != null && this.node.cubes.size() <= 1)   return

        def cube = new ClassCube(nDims)

        def previousNode = node
        def currentNode = previousNode.father

        while (currentNode != null) {
            def dim = currentNode.model.splitDim
            if (currentNode.isLesserChild(previousNode)) {
                double oldUpper = cube.getUpper(dim)
                cube.setUpper(dim, Math.min(oldUpper, currentNode.model.splitPoint))
            } else {
                double oldLower = cube.getLower(dim)
                cube.setLower(dim, Math.max(oldLower, currentNode.model.splitPoint))
            }
            previousNode = currentNode
            currentNode = previousNode.father
        }
        cube.setClassData(classData)
        node.boxes = [cube]
    }

    /**
     * Computes the split and sets fields of "this" accordingly
     * @return results as a pair (List-of-left-boxes, list-of-right-boxes); also sets internal fields!
     */
    // todo: move the computation and splitting of the testInstances to class Pruning to allow different pruning strategies
    List<List<ClassCube>> computeSplit() {

        // log.info("Starting model.computeSplit for ${this.cubes.size()} cubes")
        // 1. for each dim, get best split according to optimization criterion
        double minCriterionValue = Double.MAX_VALUE
        int bestDimension = -1
        Map resultsBestDim

        for (int dim in 0..<nDims) {
            // 1a. remember for each R(dim) the sets of cut cubes and what cubes are left and which right of h(R(dim)
            // def t0 = tic()
            Map resultThisDim = scanBoxesOneDim(dim)
            // def t1 = tic()
            // log.info("NodeModel: scanning dim $dim took ${toDiffString(t0, t1)}")

            // 2a. Find best dim and R(dim)
            def minCriterionForDim = resultThisDim.minCriterionValue
            if (minCriterionForDim < minCriterionValue) {
                minCriterionValue = minCriterionForDim
                bestDimension = dim
                resultsBestDim = resultThisDim
            }
        }
        if (bestDimension < 0)  // just for debugging
            assert bestDimension >= 0, "No dimension with best splitpoint found: probably at most one bounded box in each dimension."
        // 2b. best split-dim and split-point are now in resultsBestDim
        this.splitDim = bestDimension
        this.splitPoint = resultsBestDim.bestCutpoint

        // 3. Cut the intersected cubes (=> two new small sets C_left and C_right) AND unbounded cubes and record all unwanted cubes
        def cutResults = cutIntersectedBoxes(resultsBestDim) // result is [lowerSplitted: lowers, upperSplitted: uppers, fathers: fathers]

        Collection boxesToBeCut = (Collection) cutResults.fathers
        numBoxesCut = boxesToBeCut.size()

        // 4. Create TreeOfBoxesNode for cubes left of h(R(dim) \cup C_left and right of h(R(dim) \cup C_right
        SortedMap<Double, Pair> boundaries2cubes = resultsBestDim.boundaries2cubes
        List<List<ClassCube>> segregationResult = segregateBoxesBySplitpoint(boundaries2cubes, (Collection<ClassCube>) cutResults.lowerSplitted,
                (Collection<ClassCube>) cutResults.upperSplitted, (Collection<ClassCube>) cutResults.fathers, this.splitPoint)

        // log.info "Entered with ${cubes.size()} boxes, of which ${boxesToBeCut.size()} were cut => created set with ${segregationResult[0].size()} lower and ${segregationResult[1].size()} upper boxes"
        return segregationResult
    }

    protected Map scanBoxesOneDim(int dim) {
        /*
        boundaries2cubes - a data structure for sorting all cubes along their boundaries.
        Each key of this sorted map is a boundary b, and a value is a pair of 2 lists: one of cubes starting at b (pair.first)
        and second (pair.second) of cubes ending at b
         */
        SortedMap<Double, Pair> boundaries2cubes = new TreeMap<Double, Pair>()
        List<ClassCube> unboundedCubes = []
        int[] classFrequenciesBounded = new int[this.numClasses]

        // 1. fill in the starts and ends for (half)bounded cubes
        for (ClassCube cube in node.boxes) {
            if (cube.isBounded(dim)) {
                addCubeByBoundaries(cube, dim, boundaries2cubes)
                int classIndex = (int) cube.getClassValue()
                classFrequenciesBounded[classIndex] += 1
            } else {
                // we do not add unbounded, because they are always cut!
                unboundedCubes << cube
            }
        }
        optimizer.reset(unboundedCubes.size(), classFrequenciesBounded)
        // optimizer.numUnbounded = unboundedCubes.size()
        // optimizer.classFrequenciesBounded = classFrequenciesBounded

        if (boundaries2cubes.size() < 3) {
            // we are ready as there is at most one box (or 2 boundaries) at this dimension
            def result = [dim: dim, minCriterionValue: Double.MAX_VALUE, bestCutpoint: Double.NaN, unboundedCubes: unboundedCubes]
            return result
        }

        int numKeys = boundaries2cubes.size()
        int loopIndex = 0
        // do da loop - as b2c is sorted, iteration over keySet happens in natural order (http://goo.gl/xKdpG)
        for (cutpoint in boundaries2cubes.keySet()) {
            // 1. get starting and ending cubes at cutpoint
            Pair<List, List> pair = boundaries2cubes.get(cutpoint)
            List<ClassCube> starting = pair.first   // Starting boxes at this cutpoint
            List<ClassCube> ending = pair.second    // Boxes ending at this cutpoint
            // log.info "Scan dim $dim w/ cutpoint $cutpoint; #starting boxes = ${starting.size()}, #ending boxes = ${ending.size()}"

            // update active set (for ending boxes)
            optimizer.removeEndingBoxes(ending)

            // 2. minimum computation (disregard first and last cutpoint)
            if (0 < loopIndex && loopIndex < numKeys - 1) {
                optimizer.testForNewOptimum(cutpoint)
            }

            // update active set (for starting boxes)
            optimizer.addStartingBoxes(starting)

            loopIndex++
        }

        def result = [dim: dim, minCriterionValue: optimizer.getMinCriterionResult(), bestCutpoint: optimizer.getBestCutpoint(),
                boundaries2cubes: boundaries2cubes, unboundedCubes: unboundedCubes, bestActiveSet: optimizer.getBestActiveSet()]
        return result
    }

    private addCubeByBoundaries(ClassCube cube, int dim, Map<Double, Pair> boundaries2cubes) {
        double lower = cube.getLower(dim)
        Pair<List, List> pair = getPairOrCreate(lower, boundaries2cubes)
        pair.first << cube

        double upper = cube.getUpper(dim)
        pair = getPairOrCreate(upper, boundaries2cubes)
        pair.second << cube
    }

    private Pair getPairOrCreate(double key, Map<Double, Pair> boundaries2cubes) {
        if (boundaries2cubes.containsKey(key))
            return boundaries2cubes.get(key)
        else {
            def pair = new Pair<List, List>()
            // todo: later - more efficient is to create only one list (in a separate method)
            pair.first = new ArrayList(2)
            pair.second = new ArrayList(2)
            boundaries2cubes.put(key, pair)
            return pair
        }
    }

    /**
     *
     * @param resultForDim a map with same format as returned by scanBoxesOneDim
     * @return
     */
    protected Map cutIntersectedBoxes(Map resultForDim) {
        // the in-paramer can have two forms:
        // A. only unbounded boxes are cut (if they exist) - we don't want to cut at this dim, sth is wrong!
        // def result = [dim: dim, minActive: unboundedCubes.size(), bestCutpoint: Double.NaN, unboundedCubes: unboundedCubes]
        // B. there are (possibly) active set boxes in addition to unbounded
        // [dim: dim, minActive: unboundedCubes.size() + minActive, bestCutpoint: bestCutpoint, boundaries2cubes: boundaries2cubes, unboundedCubes: unboundedCubes, bestActiveSet: bestActiveSet]

        double bestCutpoint = resultForDim.bestCutpoint
        if (Double.isNaN(bestCutpoint)) {   // for debugging, to stop before assert fails
            assert !Double.isNaN(bestCutpoint), "Best dim for tree cutpoint has only unbounded boxes to be cut (resultForDim = $resultForDim)"
        }

        // 1. cut all boxes in resultForDim.unboundedCubes and in resultForDim.bestActiveSet and record fathers for filtering
        List lowers = []
        List uppers = []
        def fathers = new HashSet<ClassCube>()
        int dim = resultForDim.dim
        Collection<ClassCube> unboundedCubes = resultForDim.unboundedCubes
        for (cube in unboundedCubes) {
            def pair = cube.splitAtDim(cube, dim, bestCutpoint)
            lowers << pair.first
            uppers << pair.second
            fathers << cube
        }
        Collection<ClassCube> bestActiveSet = resultForDim.bestActiveSet
        for (cube in bestActiveSet) {
            def pair = cube.splitAtDim(cube, dim, bestCutpoint)
            lowers << pair.first
            uppers << pair.second
            fathers << cube
        }

        return [lowerSplitted: lowers, upperSplitted: uppers, fathers: fathers]
    }

    protected List<List<ClassCube>> segregateBoxesBySplitpoint(SortedMap<Double, Pair> boundaries2cubes, Collection<ClassCube> lowerSplitted, Collection<ClassCube> upperSplitted, Collection<ClassCube> unwantedBoxes, double splitPoint) {
        // 1. Just copy lowerSplitted / upperSplitted and add to them the other ones (we could re-use the args but this is dangerous)
        List<ClassCube> lowers = []
        lowers.addAll lowerSplitted
        List<ClassCube> uppers = []
        uppers.addAll upperSplitted

        // 2a. Get all in boundaries2cubes whose upper is <= splitPoint and add to lowers but filter out all from unwantedBoxes
        // 2b. Get all in boundaries2cubes whose lower is >= splitPoint and add to uppers but filter etc.
        for (Double position in boundaries2cubes.keySet()) {
            if (position <= splitPoint) {
                //2a. add lowers
                List<ClassCube> endingAtPosition = (List<ClassCube>) boundaries2cubes[position].second    // boxes whose upperBounds are at position
                for (cube in endingAtPosition) {
                    if (!unwantedBoxes.contains(cube))
                        lowers << cube
                }
            }
            if (position >= splitPoint) {
                //2b. add uppers
                List<ClassCube> startingAtPosition = (List<ClassCube>) boundaries2cubes[position].first   // boxes whose lowerBounds are at position
                for (cube in startingAtPosition) {
                    if (!unwantedBoxes.contains(cube))
                        uppers << cube
                }
            }
        }

        return [lowers, uppers]
    }

    @Override
    public String toString() {

        if (node.isLeaf()) {
            return "NodeModel{leaf, classData=$classData}"
        } else
            return "NodeModel{inner, splitDim=$splitDim, splitPoint=$splitPoint}";
    }
}

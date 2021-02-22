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
import cubes.BoxSet
import cubes.Cube

/**
 * Created by: Artur Andrzejak 
 * Date: 16.06.12
 * Time: 23:36
 * Performs ranking of boxes in a collection according to importance, and removes the least important ones
 * todo: encapsulate the methods for each approach in own subclass and call via same interface (low priority)
 */
class RankedBoxesPruning {

    BoxSet boxes

    // Type of pruning / filtering, see switch in filterBoxes() below
    int  pruningParam

    ExperimentResult results
    int maxBoxesToKeep

    // Box collection after filtering, we use a field for convenience
    private Collection<ClassCube> out

    RankedBoxesPruning (BoxSet _boxes, int  _pruningParam) {
        this.boxes = _boxes
        this.pruningParam = _pruningParam
        results = ExperimentResultSingletonHolder.getInstance()
        assert results != null, "Could not obtain an ExperimentResult instance"

        maxBoxesToKeep = results.getInt("PprunMaxBoxes")
    }


    BoxSet filterBoxes() {

        int finalNumBoxes = Math.min(maxBoxesToKeep, boxes.size())

        // select the box filtering strategy via pruningParam - see explanations at Prun in Json parameter file
        out = new BoxSet(finalNumBoxes)
        switch (pruningParam) {
            case 10:
                keepAnyBoxes()
                break
            case 20:
                keepBoxesWithHighestNumInstances()
                break
            case 30..39:
                keepBoxesWithHighestNumInstances_and_AtLeastXinstancesPerBox()
                break
            case 100..199:
                keepBoxesWithHighestRelativeCriterion("relThickness")
                break
            case 200..299:
                keepBoxesWithHighestRelativeCriterion("relVolume")
                break
            case 300..399:
                keepBoxesWithHighestRelativeCriterion("classImpurity")
                break
            default:
                assert 0, "Wrong value of pruningParam  ($pruningParam)"
        }
        return out

    }

    // Simplest filtering which keeps at most finalCount boxes which are "first" in the collection boxes
    def keepAnyBoxes() {

        if (keepAllIfCollectionBelowLimit())
            return
        // copy only a subset
        int counter = maxBoxesToKeep
        for (box in boxes) {
            out << box
            counter--
            if (counter <= 0)
                break
        }
    }

    // Checks, whether all boxes can be included in out, and if yes, copies all refs into "out"
    private boolean keepAllIfCollectionBelowLimit() {
        if (boxes.size() <= maxBoxesToKeep) {
            // just copy all
            out.addAll(boxes)
            return true
        } else
            return false
    }

    // For ranking boxes by # of instances inside
    private final static Comparator<ClassCube> BoxByInstanceNumber_COMPARATOR = new Comparator<ClassCube>() {

        int compare(ClassCube cubeA, ClassCube cubeB) {
            return (cubeB.numInstances).compareTo(cubeA.numInstances)
        }
    }

    // Keep at most maxBoxesToKeep boxes which have highest number of instances
    def keepBoxesWithHighestNumInstances() {

        if (keepAllIfCollectionBelowLimit())
            return

        // initialize the priority queue with default initial capacity
        def rankedCubes = new PriorityQueue<ClassCube>(boxes.size(), BoxByInstanceNumber_COMPARATOR)
        // put all boxes and rank them while adding
        rankedCubes.addAll(boxes)

        // now get at most finalNumBoxes many out of rankedCubes, in order of #instances
        for (i in 0..<maxBoxesToKeep) {
            if (rankedCubes.empty)  // are any candidates there?
                break
            def nextBox = rankedCubes.poll()
            out << nextBox
        }
    }


    // Keep at most maxBoxesToKeep boxes which have highest number of instances, BUT also with at least
    // (pruningParam-30)+1 instances inside of each of them
    def keepBoxesWithHighestNumInstances_and_AtLeastXinstancesPerBox() {
        keepBoxesWithHighestNumInstances()

        // now remove all box which have not enough instances inside
        int minNumInstances = (pruningParam-30)+1
        assert minNumInstances >= 0, "Wrong encoding of minNumInstances via pruningParam (minNumInstances = $minNumInstances, pruningParam = $pruningParam)"

        // assume that "out" is a list, and start removing from the end (lowest coverages per box)
        assert out instanceof List, "Field out should be of type List for this filtering method"
        List<ClassCube> outList = (List<ClassCube>) out

        int lastIndex = outList.size()-1
        while (lastIndex >= 0 && (outList[lastIndex]).numInstances < minNumInstances) {
            outList.remove(lastIndex)
            lastIndex--
        }
        // println "Keeping ${lastIndex+1} boxes"
    }

    // Keep at most maxBoxesToKeep boxes which have highest relative volume or thickness, or class impurity ...
    def keepBoxesWithHighestRelativeCriterion(String sortCriterium) {

        if (keepAllIfCollectionBelowLimit())
            return

        List<CubeMetrics>  cubeMetrics
        switch (sortCriterium) {
            case "relVolume":
                cubeMetrics = computeRelVolumeAndThickness()
                cubeMetrics.sort { - it.relVolume}      // sort from larger to smaller
                break
            case "relThickness":
                cubeMetrics = computeRelVolumeAndThickness()
                cubeMetrics.sort {- it.relThickness}    // sort from larger to smaller
                break
            case "classImpurity":
                cubeMetrics = computeAllClassImpurities()
                cubeMetrics.sort { it.classImpurity}    // sort from smaller to larger
                // printCubeMetricsContainer(cubeMetrics, 1000, 1)
                break
            default:
                throw new IllegalArgumentException("Unknown sorting creterium $sortCriterium")
        }

        // now get at most finalNumBoxes many out of rankedCubes, in order of decreasing criterion value
        for (i in 0..<maxBoxesToKeep) {
            def cubeWithMetrics = cubeMetrics[i]
            out << cubeWithMetrics.inBox
        }
        // debug
        // printCubeMetricsContainer(cubeMetrics, maxBoxesToKeep, 1)
        // release the data
        cubeMetrics = null
    }


    private printCubeMetricsContainer(List<CubeMetrics>  cubeMetrics, int numToPrint, int minNumInstancesInside) {
        final int numDecimals = 4

        numToPrint = Math.min(numToPrint, cubeMetrics.size())

        def buf = new StringBuffer(200)
        buf << "#instances, classImpurity, relVolume, relThickness \n"
        for (int i = 0; i < numToPrint; i++) {
            def cubeWithMetrics = cubeMetrics[i]
            int numInstancesInside = cubeWithMetrics.inBox.numInstances
            if (numInstancesInside >= minNumInstancesInside)
                buf << "\t\t[$i]   ${numInstancesInside}, ${cubeWithMetrics.classImpurity.round(numDecimals)}, ${cubeWithMetrics.relVolume.round(numDecimals)}, ${cubeWithMetrics.relThickness.round(numDecimals)} | "
            if (i % 4 == 0)
                buf << "\n"
        }
        println "CubeMetricsContainer ($numToPrint positions): $buf"
    }

    // Computes for each box in this.boxes its relative thickness and relative volume (each stored in CubeMetrics)
    protected List<CubeMetrics> computeRelVolumeAndThickness() {
        assert boxes.boundingBox != null, "Cannot compute rel thickness / rel volume as bounding box of box collections is null"
        Cube bb = boxes.boundingBox     // bb is the bounding box
        int nDims = bb.nDims

        // find out which dimensions in the bounding box bb are not "flat" (and not infinite) i.e. are "valid"; store them and their ranges

        // indices of valid dimensions
        List<Integer> validDims = []
        // ranges of corresponding dimensions i.e. ranges[i] is (bb.upper[validDims[i]] - bb.lower[[validDims[i]])
        List<Double> ranges = []
        for (dim in 0..<nDims) {
            def lower = bb.getLower(dim)
            def upper = bb.getUpper(dim)
            assert !Double.isInfinite(lower), "Found infinite bounding box (dim = $dim), box = $bb"
            assert !Double.isInfinite(upper), "Found infinite bounding box (dim = $dim), box = $bb"
            if (upper > lower) {
                // found valid dimension
                validDims << dim
                ranges << (upper - lower)
            }
        }

        // compute volume of the bounding box
        double bbVolume = 1.0
        for (double range in ranges)
            bbVolume *= range

        // iterate over all boxes and compute rel. thickness + rel. volume individually
        List<CubeMetrics> result = new ArrayList<CubeMetrics>(boxes.size())
        for (ClassCube box in boxes) {
            CubeMetrics cubeMetrics = new CubeMetrics(box)
            cubeMetrics.computeVolumeAndThickness(validDims, ranges, bbVolume, bb)
            result << cubeMetrics
        }
        return result
    }

    // Computes for each box in this.boxes its classImpurity (each stored in CubeMetrics)
    protected computeAllClassImpurities() {
        // iterate over all boxes and compute relative impurity individually
        List<CubeMetrics> result = new ArrayList<CubeMetrics>(boxes.size())
        for (ClassCube box in boxes) {
            CubeMetrics cubeMetrics = new CubeMetrics(box)
            cubeMetrics.computeClassImpurity()
            result << cubeMetrics
        }
        return result
    }

}

// Auxiliary "decorator" class which stores relative thickness and relative volume for the referenced inBox
private class CubeMetrics {
    /** The log of 2. */
    protected static double log2 = Math.log(2);

    // relative volume: ratio of cube volume to box collection's bounding box
    double relVolume

    // relative thickness: among all dims i, min of { ratio of side-length-in-dim i to range-of-bounding-box-in-dim i}
    double relThickness

    // classImpurity: this is the entropy of the class prop vector; low values are better (single class wins)
    double classImpurity

    // reference to the described object
    ClassCube inBox

    // constructor
    def CubeMetrics (ClassCube _inBox) {
        this.inBox = _inBox
    }

    // computes and stores relative thickness and relative volume of the referenced inBox
    def computeVolumeAndThickness (List<Integer> validDims, List<Double> bbRanges, double bbVolume, Cube boundingBox) {
        // relThickness is used as minimum
        relThickness = Double.POSITIVE_INFINITY
        relVolume = 1.0

        int numValidDims = validDims.size()

        for (int i = 0; i < numValidDims; i++) {
            // get the current dimension
            int dim = validDims[i]
            double lower, upper
            // compute intersection of the lower and upper bounds for dim intersected with the bb
            lower = Math.max(inBox.getLower(dim), boundingBox.getLower(dim))
            upper = Math.min(inBox.getUpper(dim), boundingBox.getUpper(dim))
            double range = upper - lower
            if (range < 0)
                assert range >= 0, "Negative dimension encountered for a box; lower = $lower, upper = $upper, box = $inBox, bb = $boundingBox"

            // compute volume (here still absolute, not relative)
            relVolume *= range

            // compute relThickness
            double relRange = range / bbRanges[i]
            if (relThickness > relRange) {
                // new minimum found
                relThickness = relRange
            }
        }
        // change absolute volume to relative one
        relVolume /= bbVolume
    }

    // computes and stores relative thickness and relative volume of the referenced inBox
    def computeClassImpurity() {
        double[] classProbDistribution = this.inBox.classData.classProbDistribution

        this.classImpurity = 0
        for (prob in classProbDistribution)
            classImpurity += logFunc(prob)
    }


    /**
     * Help method for computing entropy.
     */
    public final double logFunc(double num) {
        // Constant hard coded for efficiency reasons
        if (num < 1e-6)
            return 0;
        else
            return -num*Math.log(num)/log2;
    }
}
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

import com.google.common.base.Objects

/*
* Container for a d-dimensional iso-oriented box derived from a tree path
*/
@Typed
// see http://goo.gl/iOVYQ, http://goo.gl/VUaKh
class ClassCube extends Cube implements Cloneable {
    def static final EMPTY_CLASSCUBE = new ClassCube(0)

    // Contains info on class for this cube (classValue, confidence, distribution etc.)
    ClassData classData = new ClassData()

    ClassCube(int numberDimensions) {
        super(numberDimensions)
    }

    boolean equals(o) {
        ClassCube otherClassCube = (ClassCube) o
        super.equals(o) && classData.equals(otherClassCube?.classData)
    }

    int hashCode() {
        super.hashCode()
    }

    @Override
    ClassCube clone() {
        ClassCube result = (ClassCube) super.clone()
        result.classData = (ClassData) classData.clone()
        return result
    }

    def setClassValue(double newClassValue) {
        classData.classValue = newClassValue
    }

    def double getClassValue() {
        return classData?.classValue
    }

    def setConfidence(double newConfidence) {
        classData.confidence = newConfidence
    }

    def setClassProbDistribution(double[] newClassProbDistribution) {
        classData.classProbDistribution = newClassProbDistribution
    }

    /** Returns the intersection of the this and otherCube, null if they do not intersect. */
    ClassCube getIntersection(ClassCube otherCube) {
        ClassCube result = (ClassCube) this.intersectBounds(otherCube)
        if (result != null) {
            result.classData.mergeClassData otherCube.classData
        }
        return result
    }

    // todo: remove or move to an "OptionalTools" class - never used except for tests
    /** Returns a set of ClassCubes which encompass the space which is not in otherCube
     * The classData of the results references this.classData*/
    Set<ClassCube> minusFromOtherCube(ClassCube otherCube) {
        return minusFromIntersection(getIntersection(otherCube))
    }

    // todo: remove or move to an "OptionalTools" class - never used except for tests
    /** Returns a set of ClassCubes from this which encompass the space which is not in intersection,
     * the user has to provide the correct intersection
     * The classData of the results references this.classData*/
    Set<ClassCube> minusFromIntersection(ClassCube intersection) {
        // Boxes do not intersect at all, return original
        if (intersection == null)
            return [this]

        // OtherCube contains this cube, return empty set
        if (intersection.bounds == bounds)
            return []

        Set<ClassCube> result = []

        ClassCube originalBox = this.clone()

        for (int i in 0..<nDims) {
            if (intersection.isBounded(i)) {
                if (originalBox.getLower(i) < intersection.getLower(i)) {
                    ClassCube newCube = originalBox.clone()
                    newCube.classData = this.classData // Reuse classData to conserve memory
                    newCube.setUpper(i, intersection.getLower(i))
                    result << newCube
                    originalBox.setLower(i, intersection.getLower(i))
                }
                if (originalBox.getUpper(i) > intersection.getUpper(i)) {
                    ClassCube newCube = originalBox.clone()
                    newCube.classData = this.classData // Reuse classData to conserve memory
                    newCube.setLower(i, intersection.getUpper(i))
                    result << newCube
                    originalBox.setUpper(i, intersection.getUpper(i))
                }
            }
        }

        return result
    }

    /**
     * Attempts to join this and otherCube along dim
     * @param otherCube
     * @param dim dimension at which both cubes are joined
     * @return if not possible, EMPTY_CLASSCUBE, otherwise the join
     */
    ClassCube joinAtDim(ClassCube otherCube, int dim) {

        def result = EMPTY_CLASSCUBE
        // 1. Check classValues
        def joinableClasses = this.classData.isJoinable(otherCube.classData)
        // 2. Are bounds for all other dims equal?
        if (joinableClasses && this.equalBoundsExceptOneDim(otherCube, dim)) {
            double newLower = Math.min this.getLower(dim), otherCube.getLower(dim)
            double newUpper = Math.max this.getUpper(dim), otherCube.getUpper(dim)
            result = this.clone()
            result.setBounds(dim, newLower, newUpper)
            result.classData.mergeClassData(otherCube.classData)
        }
        return result
    }

    Pair<ClassCube, ClassCube> splitAtDim(ClassCube cube, int dim, double cutPoint) {
        // int nDims = cube.nDims
        double lower = cube.getLower(dim)
        double upper = cube.getUpper(dim)

        assert lower < cutPoint && cutPoint < upper, "Attempting to split at dim $dim but cutpoint $cutPoint is outside the box-range ([$lower, $upper]) at this dim (cube: $cube)"

        def lcube = cube.clone()
        def ucube = cube.clone()

        lcube.setUpper dim, cutPoint
        ucube.setLower dim, cutPoint

        def result = new Pair(lcube, ucube)
        return result
    }


    @Override
    String toString() {
        return "$classData; ${super.toString()}".toString()
    }

    boolean geometryEquals(Object o) {
        super.equals(o)
    }

    int geometryHashCode() {
        super.hashCode()
    }

}

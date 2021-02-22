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

import weka.core.Utils
import static cubes.ClassData.CmpStrategy.CLASS_AND_DISTRIB

/**
 * User: Artur Andrzejak
 * Date: 23.01.12
 * Time: 17:48
 * Holds information on class of (classValue, confidence, prob. distribution of classes etc.)
 */
@Typed
// @ToString
// @EqualsAndHashCode(excludes = "conflictingClassData, cube")  // see http://goo.gl/iOVYQ, http://goo.gl/VUaKh
class ClassData {


    static enum CmpStrategy {
        CLASS_VAL_ONLY /* default */, CLASS_AND_DISTRIB
    }

    // Don't use ExperimentResult to switch
    static classCmpStrategy = CmpStrategy.CLASS_VAL_ONLY

    double classValue
    double confidence
    double[] classProbDistribution
    boolean hasConflict = false

    private volatile cashedHashCode
    private volatile isHashCodeValid = false


    @Override public String toString() {
        final int numDecimals = 3
        // Round all elements of classProbDistribution for nicer presentation
        List<Double> distribList = classProbDistribution.collect { ((Double)it).round(numDecimals) }
        String conflictStr = hasConflict ? ", conflt=1" : ""
        return "class=${classValue.round(0)}, distrib=${distribList}, confd=${confidence.round(numDecimals)}$conflictStr"
    }

    @Override Object clone() throws CloneNotSupportedException {
        ClassData result = new ClassData()
        result.classValue = this.classValue
        result.confidence = this.confidence
        result.hasConflict = this.hasConflict
        if (this.classProbDistribution != null) {
            result.classProbDistribution = (double[]) this.classProbDistribution.clone()
        }
        return result
    }

    @Override boolean equals(o) {
        if (this.is(o)) return true
        if (this.hashCode() != o.hashCode()) return false
        if (getClass() != o.class) return false

        ClassData classData = (ClassData) o
        if (Double.compare(classData.classValue, classValue) != 0) return false
        if (Double.compare(classData.confidence, confidence) != 0) return false
        if (hasConflict != classData.hasConflict) return false
        if (!Arrays.equals(classProbDistribution, classData.classProbDistribution)) return false

        return true
    }

    @Override int hashCode() {
        if (isHashCodeValid)
            return cashedHashCode

        int result
        long temp
        temp = classValue != +0.0d ? Double.doubleToLongBits(classValue) : 0L
        result = (int) (temp ^ (temp >>> 32))
        temp = confidence != +0.0d ? Double.doubleToLongBits(confidence) : 0L
        result = 31 * result + (int) (temp ^ (temp >>> 32))
        result = 31 * result + (classProbDistribution != null ? Arrays.hashCode(classProbDistribution) : 0)
        result = 31 * result + (hasConflict ? 1 : 0)
        cashedHashCode = result
        return result
    }

    private invalidateHashCode() {
        isHashCodeValid = false
    }

    // Merges this and  otherCData and stores in this; if conflicting, stores copies of both in conflictingClassData
    def mergeClassData(ClassData otherCData) {
        def localConflict = isLocalConflict otherCData
        // conflicts are "hereditary"
        if (localConflict || this.hasConflict || otherCData.hasConflict) {
            hasConflict = true
        }
        // arbitrary class computation (also as conflict "resolution") - take class with higher confidence
        classValue = this.confidence > otherCData.confidence ? this.classValue : otherCData.classValue
        // merging other values - quite arbitrary
        confidence = Math.min this.confidence, otherCData.confidence
        mergeProbDistributions this.classProbDistribution, otherCData.classProbDistribution
        invalidateHashCode()
    }

    // True, if the classes of "this" and otherCData do not agree - according to policy set by CLASS_COMPARISON_STRATEGY
    boolean isLocalConflict(ClassData otherCData) {
        boolean result = this.classValue != otherCData.classValue

        switch (classCmpStrategy) {
            case CLASS_AND_DISTRIB:
                if (result) return result
                int distribSize = this.classProbDistribution?.size()
                assert distribSize == otherCData.classProbDistribution?.size()
                for (int i in 0..<distribSize) {
                    if (this.classProbDistribution != otherCData.classProbDistribution)
                        return true
                }
                return false
            default:
                return result
        }
    }


    boolean isJoinable(ClassData otherCData) {
        boolean result = this.hasConflict || otherCData.hasConflict || this.isLocalConflict(otherCData)
        return (!result)
    }


    def mergeProbDistributions(double[] probDistA, double[] probDistB) {
        // check lengths
        int lenA = probDistA?.length
        int lenB = probDistB?.length
        if (lenA > 0 && lenB > 0) {
            assert lenA == lenB, "Vectors of class. prob. distributions to be merged have different lengths ($lenA and $lenB)"
            this.classProbDistribution = new double[lenA]
            double sum = 0  // just to check, whether probDist were already normalized
            for (int i = 0; i < lenA; i++) {
                double average = (probDistA[i] + probDistB[i]) / 2
                classProbDistribution[i] = average
                sum += average
            }
            assert Math.abs(sum - 1.0) <= Utils.SMALL, "Merged class. prob. distribution vector is not normalized (is $sum, should be 1.0)"
        } else // else : no vector
            classProbDistribution = null
        invalidateHashCode()
    }

    // finds an index of a class with highest prob.
    int getIndexOfClassWithHighestProb() {
        assert classProbDistribution != null && classProbDistribution.length > 0
        Utils.maxIndex(classProbDistribution)
    }

    /**
     * Sets the classValue to the index of class with highest value in classProbDistribution
     * @return
     */
    def setClassToValueWithHighestProbabilityInDistribution() {
        def classIndex = getIndexOfClassWithHighestProb()
        this.classValue = (double) classIndex
        this.confidence = classProbDistribution[classIndex]
        invalidateHashCode()
    }

    def clearConflict() {
        hasConflict = false
        invalidateHashCode()
    }
}
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

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap

@Typed
class IntersectionTracker {

    /* Stores the index of the cube in the intersection array */
    private BiMap<Cube, Integer>[] cubeIndices = new BiMap<Cube, Integer>[2]
    /* track assigned indices */
    private int[] nextIndex = [0, 0]

    /* The intersection array, first index is collection0, second is collection1 */ // TODO: Make sparse?
    private int[][] intersections

    IntersectionTracker(Collection<Cube> cubesA, Collection<Cube> cubesB) {
        def sizeA = cubesA.size()
        def sizeB = cubesB.size()
        intersections = new int[sizeA][sizeB]
        cubeIndices = [HashBiMap.create(sizeA), HashBiMap.create(sizeB)]
        //cubesA.eachWithIndex { Cube cubeA, int i ->  cubeIndices[0][cubeA] = i} // Argh, groovypp issue 354
        int currIndex = 0
        for (cube in cubesA)
            cubeIndices[0][cube] = currIndex++
        currIndex = 0
        for (cube in cubesB)
            cubeIndices[1][cube] = currIndex++
    }

    private int getIndex(Cube cube, int collectionIndex) {
        return ((Integer)cubeIndices[collectionIndex][cube]).intValue()
    }

    private Cube getCube(int index, int collectionIndex) {
        return cubeIndices[collectionIndex].inverse()[index]
    }

    void addUnbounded(Cube cube, int collectionIndex) {
        def cubeIndex = getIndex(cube, collectionIndex)
        if (collectionIndex == 0) {
            for (j in (0..<intersections[cubeIndex].size())) {
                intersections[cubeIndex][j]++
            }
        } else {
            for (i in (0..<intersections.size())) {
                intersections[i][cubeIndex]++
            }
        }
    }

    void addBoundedIntersection(Cube cubeA, Cube cubeB) {
        intersections[getIndex(cubeA, 0)][getIndex(cubeB, 1)]++
    }

    Set<Pair<Cube, Cube>> getPairsAboveOrEqualToIntersectionThreshold(int threshold) {
            def Set<Pair<Cube, Cube>> pairs = []
            for (i in (0..<intersections.size())) {
                for (j in (0..<intersections[i].size())) {
                    if (intersections[i][j] >= threshold)
                        pairs << new Pair<Cube, Cube>(getCube(i, 0), getCube(j, 1))
                }
            }
            return pairs
        }
    Set<Pair<Cube, Cube>> getPairs(int numIntersections, boolean assertMaxIntersections = true) {
                def Set<Pair<Cube, Cube>> pairs = []
                for (i in (0..<intersections.size())) {
                    for (j in (0..<intersections[i].size())) {
                        if (intersections[i][j] == numIntersections)
                            pairs << new Pair<Cube, Cube>(getCube(i, 0), getCube(j, 1))
                        else {
                            if (assertMaxIntersections && ((intersections[i][j] > numIntersections)))
                                throw new IllegalStateException("Max intersections expected $numIntersections, Pair " + getCube(i,0) +", " + getCube(j, i) + " has " + intersections[i][j] + " intersections")
                        }
                    }
                }
                return pairs
            }
}

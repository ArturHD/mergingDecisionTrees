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

import com.google.common.collect.ArrayListMultimap
import experiment.PerfUtils
import groovy.util.logging.Log

/**
 * Created by: Artur Andrzejak 
 * Date: 02.02.12
 * Time: 01:10
 * For joining adjacent cubes with "equivalent" classData
 */
@Typed @Log
class JoinAdjacentCubes implements PerfUtils {

    // Collection on which we operate
    private BoxSet cubes

    private static Integer thresholdForUsingHashSet = new Integer(50)

    // Or better a Static Factory Method?  see Effective Java #1, http://goo.gl/hpiru
    JoinAdjacentCubes(BoxSet newCubeCollection) {

/*
        // Since we use add/remove for cubes, it is worth to use a HashSet if newCubeCollection is "large"
        if (newCubeCollection instanceof List && newCubeCollection.size() > thresholdForUsingHashSet)
            this.cubes = new HashSet<ClassCube>(newCubeCollection)
        else
*/
        this.cubes = newCubeCollection
    }

    int totalJoinedCubes

    /*
       boundaries2cubes - a data structure for sorting all cubes along their boundaries.
       Each key of this sorted map is a boundary b, and a value is a pair of 2 lists: one of cubes starting at b (pair.first)
       and second (pair.second) of cubes ending at b
    */
    private SortedMap<Double, Pair> boundaries2cubes = new TreeMap<Double, Pair>()
    // Auxiliary structure which stores removed parent cubes  (to prevent using same "source" cube multiple times)
    private Set removedCubes = new HashSet<ClassCube>()

    BoxSet joinAdjacentCubes() {
        totalJoinedCubes = 0
        removedCubes.clear()

        def t0 = tic()
        if (cubes.size() > 0) {
            def dims = cubes.iterator().next().getnDims()
            for (int dim in 0..<dims) {
                joinCubesOneDim(dim)
            }
        }
        def t1 = tic()
        int numRemovedCubes = removedCubes.size()
        boundaries2cubes.clear()
        removedCubes.clear()
        // log.info "Run of joinAdjacentCubes on ${cubes.size()} (joined: $totalJoinedCubes ,  removed: $numRemovedCubes) took: ${toDiffString(t0, t1)}"
        // assert 2*totalJoinedCubes == numRemovedCubes, "# joined and # removed are wrong!"
        return cubes
    }



    protected joinCubesOneDim(int dim) {
        boundaries2cubes.clear()
        // 1. fill in the starts and ends for (half)bounded cubes
        for (ClassCube cube in cubes) {
            if (cube.isBounded(dim)) {
                addCubeByBoundaries(cube, dim, boundaries2cubes)
            }
        }

        // 2a. iterate all starting boundaries, for each find all cubes with same ending b. and check the Carthesian product of both lists
        for (cutpoint in boundaries2cubes.keySet()) {
            // 1. get starting and ending cubes at cutpoint
            Pair<List, List> pair = boundaries2cubes.get(cutpoint)
            List<ClassCube> starting = pair.first   // Starting boxes at this cutpoint
            List<ClassCube> ending = pair.second    // Boxes ending at this cutpoint

            // 2b. generate Carthesian product of startingCubes, endingCubes, check and possibly add to joinedCubesOneDim
            if (starting?.size() > 0 && ending?.size() > 0)
                checkAllPairsAndAdd(starting, ending, dim)
        }
    }


    protected checkAllPairsAndAdd(List<ClassCube> cubesA, List<ClassCube> cubesB, int dim) {

        ArrayListMultimap<Double, ClassCube> cubesAMap = ArrayListMultimap.create()
        ArrayListMultimap<Double, ClassCube> cubesBMap = ArrayListMultimap.create()

        for (cA in cubesA)
            cubesAMap.put(Double.valueOf(cA.classValue), cA)
        for (cB in cubesB)
            cubesBMap.put(Double.valueOf(cB.classValue), cB)


        for (classValue in cubesAMap.keySet()) {
            List<ClassCube> cAs = cubesAMap.get(classValue)
            List<ClassCube> cBs = cubesBMap.get(classValue)

            for (cA in cAs) {
                if (removedCubes.contains(cA))
                    continue
                for (cB in cBs) {
                    if (removedCubes.contains(cB))
                        continue
                    ClassCube join = cA.joinAtDim(cB, dim)
                    // 2. if joinable, join, remove parents from cubes and add result to cubes
                    if (join != ClassCube.EMPTY_CLASSCUBE) {
                        // debug
/*
                    if (!cubes.contains(cA))
                        log.warning("########## Cube not found in cubes collection (col. size=${cubes.size()}, cube = $cA)")
                    if (!cubes.contains(cB))
                        log.warning("########## Cube not found in cubes collection (col. size=${cubes.size()}, cube = $cB)")
                    if (cubes.contains(join))
                        log.warning("########## Cube is already in cubes collection; cube=$join")
*/
                        // remove parents, add join; since cubes is a HashSet for cubes.size > X, this is efficient
                        cubes.remove(cA)
                        cubes.remove(cB)
                        removedCubes << cA
                        removedCubes << cB

                        cubes.add(join)
                        totalJoinedCubes++
                    }
                }
            }
        }
    }

    // todo: this method and getPairOrCreate are identical as in NodeModel - make a trait for "line sweep"
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
}

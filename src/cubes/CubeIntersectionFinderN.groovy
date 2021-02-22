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

import experiment.PerfUtils
import groovy.util.logging.Log

@Typed @Log
class CubeIntersectionFinderN implements PerfUtils {
    /** Returns a set containing all pairs of cubes in cubesA and cubesB which intersect,  or an empty set if no cubes intersect*/
    Set<Pair<Cube, Cube>> findIntersections(Collection<Cube> cubesA, Collection<Cube> cubesB) {
        def startTime = tic()
        if (cubesA == null || cubesB == null) return []
        if (cubesA.size() == 0 || cubesB.size() == 0) return []

        def intersectionTracker = new IntersectionTracker(cubesA, cubesB)
        def dims = cubesA.iterator().next().getnDims()
        for (int dim in 0..<dims) {

            def t0 = tic()

            def boundedCubesA = cubesA.findAll {it.isBounded(dim)}
            def unboundedCubesA = cubesA.findAll {!it.isBounded(dim)}

            def boundedCubesB = cubesB.findAll {it.isBounded(dim)}
            def unboundedCubesB = cubesB.findAll {!it.isBounded(dim)}

            def t1 = tic()

            // All (in this dimension) unbounded cubesA intersect all cubesB
            for (it in unboundedCubesA)
                intersectionTracker.addUnbounded(it, 0)

            def t2 = tic()

            def SortedMap<Double, Quartet> events = new TreeMap<Double, Quartet>()
            for (it in boundedCubesA) {
                // All unbounded cubesB intersect this cubeA
                for (cubeB in unboundedCubesB) {
                    intersectionTracker.addBoundedIntersection(it, cubeB)
                }
                addCubeByBoundaries((ClassCube) it, dim, 0, events)
            }
            for (it in boundedCubesB) {
                addCubeByBoundaries((ClassCube) it, dim, 1, events)
            }

            def t3 = tic()

            //events.sort()

            def t4 = tic()

            // Changed to Set (from List) for performance
            def Set<ClassCube> openA = []
            def Set<ClassCube> openB = []

            for (x in events.keySet()) {
                Quartet<List<ClassCube>, List<ClassCube>, List<ClassCube>, List<ClassCube>> pair = events.get(x)
                List<ClassCube> startingA = pair.first
                List<ClassCube> startingB = pair.second
                List<ClassCube> endingA = pair.third
                List<ClassCube> endingB = pair.forth

                openA.removeAll(endingA)
                openB.removeAll(endingB)

                openA.addAll(startingA)
                for (a in startingA)
                    for (b in openB)
                        intersectionTracker.addBoundedIntersection(a, b)

                openB.addAll(startingB)
                for (b in startingB)
                    for (a in openA)
                        intersectionTracker.addBoundedIntersection(a, b)

            }

            def t5 = tic()

            if (timeDiff(t0, t5) > 1000)
                log.warning("CIF.getIntersections(): Processing dimension $dim of $dims took ${toDiffString(t0, t5)}: U/B split ${toDiffString(t0, t1)}, unbounded pre-processing ${toDiffString(t1, t2)}, bounded to event translation ${toDiffString(t2, t3)}, event sort ${toDiffString(t3, t4)}, event main loop  ${toDiffString(t4, t5)}")
        }

        def endTime = tic()
        if (timeDiff(startTime, endTime) > 1000)
            log.warning("CIF.getIntersections() took ${toDiffString(startTime, endTime)}! Collections had ${cubesA.size()} and ${cubesB.size()} boxes")

        return intersectionTracker.getPairs(dims)
    }

    // todo: this method and getPairOrCreate are similar to methods in NodeModel - make a trait for "line sweep"
    private addCubeByBoundaries(ClassCube cube, int dim, int collection, Map<Double, Quartet> boundaries2cubes) {
        double lower = cube.getLower(dim)
        Quartet<List<ClassCube>, List<ClassCube>, List<ClassCube>, List<ClassCube>> quartet = getQuartetOrCreate(lower, boundaries2cubes)
        if (collection == 0)
            quartet.first << cube
        else
            quartet.second << cube

        double upper = cube.getUpper(dim)
        quartet = getQuartetOrCreate(upper, boundaries2cubes)
        if (collection == 0)
            quartet.third << cube
        else
            quartet.forth << cube
    }

    private Quartet getQuartetOrCreate(double key, Map<Double, Quartet> boundaries2cubes) {
        if (boundaries2cubes.containsKey(key))
            return boundaries2cubes.get(key)
        else {
            def pair = new Quartet<List, List, List, List>()
            // todo: later - more efficient is to create only one list (in a separate method)
            pair.first = new ArrayList(2)
            pair.second = new ArrayList(2)
            pair.third = new ArrayList(2)
            pair.forth = new ArrayList(2)
            boundaries2cubes.put(key, pair)
            return pair
        }
    }

}

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

import groovy.util.logging.Log

@Typed @Log
class CubeIntersectionFinder {
    /** Returns a set containing all pairs of cubes in cubesA and cubesB which intersect,  or an empty set if no cubes intersect*/
    static Set<Pair<Cube, Cube>> findIntersections(Collection<Cube> cubesA, Collection<Cube> cubesB) {
        def startTime = Calendar.getInstance().getTimeInMillis()
        if (cubesA == null || cubesB == null) return []
        if (cubesA.size() == 0 || cubesB.size() == 0) return []

        def intersectionTracker = new IntersectionTracker(cubesA, cubesB)
        def dims = cubesA.iterator().next().getnDims()
        for (int dim in 0..<dims) {

            def t0 = Calendar.getInstance().getTimeInMillis()

            def boundedCubesA = cubesA.findAll {it.isBounded(dim)}
            def unboundedCubesA = cubesA.findAll {!it.isBounded(dim)}

            def boundedCubesB = cubesB.findAll {it.isBounded(dim)}
            def unboundedCubesB = cubesB.findAll {!it.isBounded(dim)}

            def t1 = Calendar.getInstance().getTimeInMillis()
            def splitTime = t1 - t0

            // All (in this dimension) unbounded cubesA intersect all cubesB
            for (it in unboundedCubesA)
                intersectionTracker.addUnbounded(it, 0)

            def t2 = Calendar.getInstance().getTimeInMillis()
            def unboundedPreProcTime = t2 - t1

            def List<Event> events = new ArrayList<Event>(boundedCubesA.size() * boundedCubesB.size() * 2)
            for (it in boundedCubesA) {
                // All unbounded cubesB intersect this cubeA
                for (cubeB in unboundedCubesB) {
                    intersectionTracker.addBoundedIntersection(it, cubeB)
                }
                events << new Event(it.getLower(dim), Event.EventType.A_START, it)
                events << new Event(it.getUpper(dim), Event.EventType.A_END, it)
            }
            for (it in boundedCubesB) {
                events << new Event(it.getLower(dim), Event.EventType.B_START, it)
                events << new Event(it.getUpper(dim), Event.EventType.B_END, it)
            }

            def t3 = Calendar.getInstance().getTimeInMillis()
            def boundedPreProcTime = t3 - t2

            events.sort()

            def t4 = Calendar.getInstance().getTimeInMillis()
            def eventSortTime = t4 - t3

            // Changed to Set (from List) for performance
            def Set<Cube> openA = []
            def Set<Cube> openB = []


            events.each {
                Event event ->
                switch (event.type) {
                    case Event.EventType.A_START:
                        openA << event.cube
                        for (it in openB) {
                            intersectionTracker.addBoundedIntersection(event.cube, it)
                        }
                        break
                    case Event.EventType.B_START:
                        openB << event.cube
                        for (it in openA) {
                            intersectionTracker.addBoundedIntersection(it, event.cube)
                        }
                        break
                    case Event.EventType.A_END:
                        openA.remove(event.cube)
                        break
                    case Event.EventType.B_END:
                        openB.remove(event.cube)
                }
            }

            def t5 = Calendar.getInstance().getTimeInMillis()
            def eventProcTime = t5 - t4

            def dimProcTime = t5 - t0

            if (dimProcTime > 1000)
                log.warning("CIF.getIntersections(): Processing dimension $dim of $dims took $dimProcTime ms: U/B split $splitTime ms, unbounded pre-processing $unboundedPreProcTime ms, bounded to event translation $boundedPreProcTime ms, event sort $eventSortTime ms, event main loop $eventProcTime ms")
        }

        def runTime = Calendar.getInstance().getTimeInMillis() - startTime
        if (runTime > 1000)
            log.warning("CIF.getIntersections() took $runTime ms! Collections had ${cubesA.size()} and ${cubesB.size()} boxes")

        return intersectionTracker.getPairs(dims)
    }

}

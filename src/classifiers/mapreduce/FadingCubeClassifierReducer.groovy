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

package classifiers.mapreduce

import cubes.ClassCube
import cubes.JoinAdjacentCubes
import edu.pvs.batchrunner.ExperimentResult
import groovy.util.logging.Log
import java.util.concurrent.Callable
import weka.core.Instances
import experiment.PerfUtils
import experiment.ExperimentResultSingletonHolder
import cubes.BoxSet

/**
 * This Callable takes to Collections of Cubes and merges them via a BoxSet
 */
@Typed @Log
class FadingCubesClassifierReducer implements Callable<Collection<ClassCube>[]>, PerfUtils {

    private BoxSet cubeContainer
    private Collection<ClassCube>[] cubesA
    private Collection<ClassCube>[] cubesB
    private boolean recordStatistics
    private ExperimentResult results
    private final Instances data

    FadingCubesClassifierReducer(Instances data, Collection<ClassCube>[] cubesA, Collection<ClassCube>[] cubesB,
                                 boolean recordStatistics = false) {

        results = ExperimentResultSingletonHolder.getInstance()
        cubeContainer = new BoxSet()
        this.cubesA = cubesA
        this.cubesB = cubesB
        this.data = data
        this.recordStatistics = recordStatistics
    }

    @Override
    Collection<ClassCube>[] call() {

        def t0 = tic()

        Collection<ClassCube>[] result = new Collection<ClassCube>[cubesA.length + cubesB.length]
        int index = 0
        int indexA = 0
        int indexB = 0

        // join adjacent cubes if necessary
        if (results && Integer.parseInt(results.Pmerg as String) >= 2) {

            int joinedCubes = 0
            if (cubesA.length == 1) {
                def joiner = new JoinAdjacentCubes(new BoxSet(cubesA[0]))
                result[index++] = joiner.joinAdjacentCubes().asList()

                joinedCubes += joiner.totalJoinedCubes
                indexA++
            }
            if (cubesB.length == 1) {
                def joiner = new JoinAdjacentCubes(new BoxSet(cubesB[0]))
                result[index++] = joiner.joinAdjacentCubes().asList()

                joinedCubes += joiner.totalJoinedCubes
                indexB++
            }
            def t1 = tic()

            results.mergedBoxesCount = (results.mergedBoxesCount as int) + joinedCubes
            (results.ccMergingPerf as List<Pair<Pair<Long, Long>, Pair<Long, Long>>>) << new Pair(t0, t1)
        }

        for (int i = indexA; i < cubesA.length; i++)
            result[index++] = cubesA[i]

        for (int i = indexB; i < cubesB.length; i++)
            result[index++] = cubesB[i]

        return result
    }
}

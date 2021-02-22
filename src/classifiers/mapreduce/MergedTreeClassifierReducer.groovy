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
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import groovy.util.logging.Log
import java.util.concurrent.Callable
import weka.core.Instances
import classifiers.MergedTreeClassifier
import trees.TreeFromBoxesBuilder
import cubes.BoxSet

import static classifiers.mapreduce.MergedTreeClassifierReducer.*

/**
 * This Callable takes to Collections of Cubes and merges them via a BoxSet
 */
@Typed @Log
class MergedTreeClassifierReducer implements Callable<MergedTreeClassifier.ProcessingResult>, PerfUtils {

    private BoxSet resultingBoxSet
    private final Instances data
    private BoxSet cubesA
    private BoxSet cubesB
    private boolean recordStatistics
    private ExperimentResult results
    private final int prune
    private Instances pruningData
    private final int id
    private final int depth

    private final int highestMapID
    private final String traceID

    MergedTreeClassifierReducer(int depth, int identifier, Instances data, Collection<ClassCube> cubesA,
                                Collection<ClassCube> cubesB, int prune, Instances pruningData,
                                boolean recordStatistics = false, String traceID, int highestMapID) {

        this.depth = depth + 1
        this.id = (identifier / 2)

        results = ExperimentResultSingletonHolder.getInstance()
        resultingBoxSet = new BoxSet()
        this.data = data
        this.cubesA = cubesA
        this.cubesB = cubesB
        this.prune = prune
        this.pruningData = pruningData
        this.recordStatistics = recordStatistics
        
        this.traceID = traceID
        this.highestMapID = highestMapID
    }

    @Override
    MergedTreeClassifier.ProcessingResult call() {

        def cubeSizeA = cubesA.size()
        def cubeSizeB = cubesB.size()

        MergedTreeClassifierReducer.log.info "==> Start of unify, box sets sizes are $cubeSizeA and $cubeSizeB ..."
        def t0 = tic()
        this.resultingBoxSet.mergeBoxSetsViaIntersections(cubesA)
        this.resultingBoxSet.mergeBoxSetsViaIntersections(cubesB)
        def t1 = tic()
        appendPerfLogEntry(results, "Unify", cubesA.size(), cubesB.size(), resultingBoxSet.size(), -1L, timeDiff(t0,t1), memDiff(t0,t1))
        MergedTreeClassifierReducer.log.info "Unify operation took ${toDiffString(t0, t1)}"

        def bDash = this.resultingBoxSet.size()

        // resolve conflicts immediately
        MergedTreeClassifierReducer.log.info "==> Starting conflict resolution on ${resultingBoxSet.size()} cubes ..."

        // TODO generates plot of the cube-collection before conflict resolution and saves that to disk
        // TODO may be destroyed afterwards
        new MergedTreeClassifier.ProcessingResult (id, depth, resultingBoxSet, pruningData,
                traceID + "-beforeConflictResolution", highestMapID)

        t1 = tic()
        def numConf = MergedTreeClassifier.resolveConflicts(data, resultingBoxSet)
        def t2 = tic()

        def bAfterConflict = resultingBoxSet.size()

        MergedTreeClassifierReducer.log.info "Finished conflict resolution, it took ${toDiffString(t1, t2)}"
        appendPerfLogEntry(results, "ConflictResolution", bDash, numConf, bAfterConflict, -1L, timeDiff(t1,t2), memDiff(t1,t2))

        int pprun = results.getInt("Pprun")
        def t3 = t2
        if (20 <= pprun && pprun < 40) {
            MergedTreeClassifierReducer.log.info "==> Starting box coverage calculation on ${resultingBoxSet.size()} cubes ..."
            t2 = tic()
            MergedTreeClassifier.updateCoverage(data,resultingBoxSet)
            t3 = tic()
            MergedTreeClassifierReducer.log.info "Finished box coverage calculation, it took ${toDiffString(t2,t3)}"
        }
        appendPerfLogEntry(results, "ComputeCoverage", resultingBoxSet.size(), -1L, resultingBoxSet.size(), -1L, timeDiff(t2,t3), memDiff(t2,t3))

        t3 = tic(); def t4 = t3
        // join adjacent cubes if necessary
        def joinedCubes = 0
        if (results.getInt("Pmerg")  >= 2) {
            MergedTreeClassifierReducer.log.info "==> Starting joining adjacent cubes on ${resultingBoxSet.size()} cubes ..."
            def joiner = new JoinAdjacentCubes(resultingBoxSet)
            resultingBoxSet = joiner.joinAdjacentCubes().asList()
            t4 = tic()

            results.mergedBoxesCount = (results.mergedBoxesCount as int) + joiner.totalJoinedCubes
            (results.ccMergingPerf as List<Pair<Pair<Long, Long>, Pair<Long, Long>>>) << new Pair(t3, t4)
            joinedCubes = joiner.totalJoinedCubes
            MergedTreeClassifierReducer.log.info "Finished joining adjacent cubes, it took ${toDiffString(t3,t4)}"
        }
        appendPerfLogEntry(results, "JoinOfAdjacentCubes", bAfterConflict, -1L, resultingBoxSet.size(), joinedCubes, timeDiff(t3,t4), memDiff(t3,t4))


        def bAfterJoin = resultingBoxSet.size()

        MergedTreeClassifierReducer.log.info "==> Starting tree building on ${resultingBoxSet.size()} cubes ..."
        t4 = tic()
        // Pruning via building a tree and converting it to cubes
        if (prune > 0) {
            // build a tree and get the cubes out of it again
            def builder = TreeFromBoxesBuilder.create(resultingBoxSet, pruningData, prune)
            builder.buildTree()

            // get BoxSet after tree building and WITH original bounding box
            resultingBoxSet = builder.getBoxSet()
            assert resultingBoxSet
        }
        def t5 = tic()
        MergedTreeClassifierReducer.log.info "Finished tree building, it took ${toDiffString(t4,t5)}"


        if (recordStatistics) {
            synchronized (results) {
                (results.cubeCountRatioAfterMerging as List<Integer>) <<
                        resultingBoxSet.size() / (cubesA.size() + cubesB.size()) * 100.0
            }
            MergedTreeClassifierReducer.log.info("""|box-counting|:
                    Merging ${cubesA.size()} and ${cubesB.size()} cubes took ${toDiffString(t0, t1)},
                    conflict resolution took ${toDiffString(t1, t2)},
                    computing coverage took ${toDiffString(t2,t3)},
                    join of adjacent cubes took ${toDiffString(t3,t4)},
                    intermediate tree build for pruning took ${toDiffString(t4, t5)}.[
                    plain merge: ${bDash}, after conflict resolution: ${bAfterConflict},
                    after joining ${joinedCubes}: ${bAfterJoin} and finally
                    after pruning: ${resultingBoxSet.size()}]""")
        }

        if (prune) {
            // Forward half of the pruning data // TODO: Is this ok? Should we forward all of the data?
            pruningData.stratify(2)
            pruningData = pruningData.trainCV(2, 1)
        }

        def t9 = tic()

        appendPerfLogEntry(results, "TotalTime", cubeSizeA, cubeSizeB, resultingBoxSet.size(), -1L, timeDiff(t0,t9), memDiff(t0,t9))

        return new MergedTreeClassifier.ProcessingResult(id, depth, resultingBoxSet, pruningData, traceID, highestMapID)
    }
}

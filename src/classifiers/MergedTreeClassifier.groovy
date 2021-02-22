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

package classifiers

import classifiers.mapreduce.MergedTreeClassifierMapper
import classifiers.mapreduce.MergedTreeClassifierReducer
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import experiment.Tools
import experiment.Visualization
import groovy.util.logging.Log
import trees.TreeFromBoxesBuilder
import trees.TreeOfBoxesNode
import weka.classifiers.Classifier
import weka.core.Attribute
import weka.core.Instance
import weka.core.Instances

import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService

import cubes.*

import static experiment.Tools.createKSplits
import static experiment.Tools.loadArff

/**
 * Created by: Artur Andrzejak 
 * Date: 04.02.12
 * Time: 18:41
 * This is the "new" classifier which is obtained by merging box sets (obtained from trees)
 * todo: 1. check - to we still need the map-reduce parts?
 * todo: 2. check - the splitting into k parts and building each box set etc. is  similar to other classifiers (Fading, Voting etc. ) - can we extract common code?
 */
@Log @Typed
class MergedTreeClassifier extends Classifier implements PerfUtils {

    /* the source of the pruning data*/
    static enum PruningDataSource {
        SEPARATE_PRUNING_DATA, TRAINING_DATA
    }

    // TODO introduce parameter for the batch runner to enable visualization for specific experiments
    private final static boolean VISUALIZE_MERGE = false

    // Root of the classification tree
    private TreeOfBoxesNode root

    // ExperimentResult with parameters and result data
    ExperimentResult results

    /**
     * Default constructor initializing the experiment.
     *
     * @param results
     */
    MergedTreeClassifier() { }

    /**
     * Triggers building of the tree from a collection of boxes
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data
     */
    @Override
    void buildClassifier(Instances data) {

        // classifier is built from the given data
        if (root == null) {

            ProcessingResult.TIME_STAMP = System.currentTimeMillis()
            ProcessingResult.VISUALIZATION.setInputFormat(data, 0, 1)

            results = ExperimentResultSingletonHolder.getInstance()
            assert results != null, "Could not retrieve ExperimentResult from ExperimentResultSingletonHolder"
            TreeFromBoxesBuilder builder = TreeFromBoxesBuilder.create(buildClassifierInternal(data, true))

            def t0 = tic()
            // finally build the resulting classifier tree
            builder.buildTree()
            def t1 = tic()

            root = builder.getTree()

            classifiers.MergedTreeClassifier.log.info("MTC: Tree building took ${toDiffString(t0, t1)}")
            ((List<Integer>) results.cutBoxesAtBuildTree) << root.treeStatistics.totalNumBoxesCut
            ((List<Integer>) results.treeDepth) << root.treeStatistics.maxRecursionDepth
        }
    }

    BoxSet buildClassifierInternal(Instances data, boolean recordStatistics = false, int k = -1) {

        BoxSet result

        results = ExperimentResultSingletonHolder.getInstance()
        if (recordStatistics)
            results.fold = (results.fold as Integer) + 1
        if (k == -1)
            k = Integer.parseInt(results.Ek as String)

        def t0 = tic()
        List splits = createKSplits(k, data, results)
        def t1 = tic()

        // merge classifier for the training data
        if (recordStatistics)
            results.ccMergingPerf = new ArrayList<Pair<Pair<Long, Long>, Pair<Long, Long>>>()

        // Here all of the processing takes place
        result = localMapReduceClassification(data, k, splits, recordStatistics)

        if (recordStatistics && (results.ccMergingPerf as List).size() > 0)
            classifiers.MergedTreeClassifier.log.info("Joining of adjacent cubes in BoxSet took ${toDiffString(new Pair<Long, Long>(0, 0), plistToP(results.ccMergingPerf as List<Pair<Pair<Long, Long>, Pair<Long, Long>>>))}")

        def t2 = tic()

        if (!results.Eparallel || Integer.valueOf((String) results.Eparallel) < 0) {
            // resolve conflicts
            resolveConflicts(data, result)
        }
        def t3 = tic()

        // join adjacent cubes after final merge if necessary
        if (results && results.Pmerg == 1) {

            def oldCubeCount = result.size()
            def joiner = new JoinAdjacentCubes(result)

            result = joiner.joinAdjacentCubes()

            if (recordStatistics) {
                results.mergedBoxesCount = (results.mergedBoxesCount as int) + joiner.totalJoinedCubes
                (results.cubeCompressionRatio as List<Integer>) << (1 - result.size() / oldCubeCount) * 100.0
            }
        }

        def t4 = tic()
        if (recordStatistics)
            classifiers.MergedTreeClassifier.log.info("MTC: Fold ${results.fold}: Splitting took ${toDiffString(t0, t1)}, cube merging took ${toDiffString(t1, t2)}, conflict resolution took ${toDiffString(t2, t3)} and adjacent cube joining took took ${toDiffString(t3, t4)}")

        if (recordStatistics)
            (results.finalCubeCount as List<Integer>) << result.size()
        return result
    }

    // This is the "core" routine of the MTC, which calls the MTC-Mapper and MTC-Reducer
    private List<ClassCube> localMapReduceClassification(Instances data, int k, List<Instances> splits = null,
                                                         boolean recordStatistics = false) {

        final List<ClassCube> result

        assert results != null, "ExperimentResult object not initialized - call ExperimentResultSingletonHolder here"
        def builder = new BuildTreeAndGetBoxSet(results)
        def prune = (k > 1) ? results.getInt("Pprun") : 0
        def pruningDataSource = PruningDataSource.valueOf((String) results.PprunDS)

        // initialize executor
        def es = Tools.getExecutorService()
        CompletionService<ProcessingResult> ecs = new ExecutorCompletionService<ProcessingResult>(es)

        // start execution (MAP)
        for (int i = 0; i < k; i++) {

            Instances split = (splits) ? splits.get(i) : loadArff("split${i}")
            ecs.submit(new MergedTreeClassifierMapper(split, i, builder, recordStatistics, prune, pruningDataSource))
        }

        final int depth = Math.floor(Math.log(k) / Math.log(2))
        final int length = k / 2
        ProcessingResult[][] intermediate = new ProcessingResult[depth][length]

        // execute (REDUCE)
        int intermediateResults = k
        while (intermediateResults > 1) {

            ProcessingResult res = ecs.take().get()

            if (intermediate[res.depth][res.identifier]) {

                def inter = intermediate[res.depth][res.identifier]

                final Instances pruningData
                if (res.highestMapID < inter.highestMapID)
                    pruningData = Tools.mergeInstances(res.associatedInstances, inter.associatedInstances)
                else
                    pruningData = Tools.mergeInstances(inter.associatedInstances, res.associatedInstances)

                def reducer = new MergedTreeClassifierReducer(res.depth, res.identifier, data, res.cubes, inter.cubes,
                        prune, pruningData, true, "($inter.traceID,$res.traceID)",
                        (res.highestMapID > inter.highestMapID) ? res.highestMapID : inter.highestMapID)

                ecs.submit(reducer)
                intermediateResults--
                intermediate[res.depth][res.identifier] = null
            } else {

                intermediate[res.depth][res.identifier] = res
            }
        }

        result = ecs.take().get().cubes
        es.shutdown()

        return result
    }


    /**
     * Resolves conflicts of the given cubes.
     */
    static int resolveConflicts(Instances trainingData, List<ClassCube> cubes) {

        def cubesCount = cubes.size()

        def resolver = new ConflictResolution(trainingData, cubes)

        int cBoxes = 0
        def it = cubes.listIterator()
        while (it.hasNext()) {
            def classCube = it.next()
            if (classCube.classData.hasConflict) {
                cBoxes++
                resolver.resolveConflict(classCube, it)
            }
        }

        def results = ExperimentResultSingletonHolder.getInstance()
        synchronized (results) {
            results.conflictBoxes = cBoxes + (int) results.conflictBoxes
            log.info """|count-boxes|:
                        Resolved conflicts for ${cBoxes} conflict cubes
                        of ${cubesCount} total cubes."""
        }

        return cBoxes
    }

    static void updateCoverage(Instances trainingData, List<ClassCube> cubes) {

        int totalInstancesInBoxes = 0

        def it = cubes.listIterator()
        while (it.hasNext()) {
            def classCube = it.next()

            // Update # of instances falling into this box
            int numInstances = classCube.numInstancesInsideCube(trainingData)
            classCube.numInstances =  numInstances
            totalInstancesInBoxes += numInstances
        }
        assert totalInstancesInBoxes >= trainingData.numInstances(), "Some instances do not covered by any box: #covered = $totalInstancesInBoxes, #instances = ${trainingData.numInstances()}"
    }

    /**
     * Predicts the class memberships for a given instance. If
     *
     * @param instance the instance to be classified
     * @return an array containing the estimated membership 
     * probabilities of the test instance in each class 
     * or the numeric prediction
     * @exception Exception if distribution could not be 
     * computed successfully
     */
    @Override
    public double[] distributionForInstance(Instance instance) throws Exception {

        double[] dist = new double[instance.numClasses()];

        switch (instance.classAttribute().type()) {

            case Attribute.NOMINAL:
                dist = root.distributionForAttributeVector(instance.toDoubleArray())
                assert dist.length == instance.numClasses(), "Distribution vector from tree has different length (${dist.length}) than supplied Instance (${instance.numClasses()})"
                break

            case Attribute.NUMERIC:
                throw new InternalError("MergedTreeClassifier does not support classification for numeric classes.")
        }

        return dist
    }

    @Override
    public String toString() {
        return String.valueOf(root)
    }

    /**
     * Structure for processing-results of map and reduce steps.
     */
    @Log @Typed
    final static class ProcessingResult {

        final static Visualization VISUALIZATION = Visualization.visualizer()

        /**
         * Will not work with conflict-resolution strategy d).
         */
        static long TIME_STAMP = 0L

        final Instances associatedInstances
        final BoxSet cubes
        final int depth
        final int identifier

        final String traceID
        final int highestMapID

        ProcessingResult(int id, int depth, BoxSet cubes, Instances instances, String traceID, int highestMapID) {

            this.identifier = id
            this.depth = depth
            this.cubes = cubes
            this.associatedInstances = instances

            this.traceID = traceID
            this.highestMapID = highestMapID

            if (VISUALIZE_MERGE) {
                VISUALIZATION.plotToFile("results/${TIME_STAMP}-mtc-${traceID}", instances, cubes, true)
            }
        }

        @Override
        String toString() {
            return "$traceID: cubes ${cubes.size()}|${Tools.getHashSum(cubes)}, instances ${(associatedInstances) ? associatedInstances.numInstances() + "|" + Tools.createHashSum(associatedInstances) : "null"}, highest map ID: $highestMapID"
        }
    }
}

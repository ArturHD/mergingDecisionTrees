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

import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder
import experiment.PerfUtils
import groovy.util.logging.Log
import java.util.concurrent.Callable
import weka.core.Instances
import weka.filters.Filter

import cubes.BuildTreeAndGetBoxSet
import classifiers.MergedTreeClassifier
import cubes.BoxSet
import experiment.Tools

/**
 * Created by IntelliJ IDEA.
 * User: flangner
 * Date: 20.02.12
 * Time: 15:59
 * To change this template use File | Settings | File Templates.
 */
@Log @Typed
class MergedTreeClassifierMapper implements Callable<MergedTreeClassifier.ProcessingResult>, PerfUtils {

    private final int id
    private final boolean recordStatistics
    private final ExperimentResult results
    private final int pruningParameter
    private final MergedTreeClassifier.PruningDataSource pruningDataSource
    private final int numSets
    private final int seed
    private final BuildTreeAndGetBoxSet builder

    private Instances split

    MergedTreeClassifierMapper(Instances split, int id, BuildTreeAndGetBoxSet builder,
                               boolean recordStatistics, int pruningParam, MergedTreeClassifier.PruningDataSource pruningDataSource, int numSets = 3, int seed = 1) {

        // parameters that might be found at ExperimentResult
        // they are initialized with default values
        this.results = ExperimentResultSingletonHolder.getInstance()

        this.split = split
        this.id = id
        this.builder = builder
        this.pruningParameter = pruningParam
        this.pruningDataSource = pruningDataSource
        this.numSets = numSets
        this.seed = seed
        this.recordStatistics = recordStatistics
    }

    @Override
    MergedTreeClassifier.ProcessingResult call() {
        def t0 = tic()

        def trainingData
        def pruningData
        if (pruningParameter > 0) {
            switch (pruningDataSource) {
                case MergedTreeClassifier.PruningDataSource.SEPARATE_PRUNING_DATA:
                    // Split of pruning data from training data
                    split.stratify(numSets);
                    trainingData = split.trainCV(numSets, numSets - 1, new Random(seed))
                    pruningData = split.testCV(numSets, numSets - 1)
                    break
                case MergedTreeClassifier.PruningDataSource.TRAINING_DATA:
                    trainingData = split
                    pruningData = split
                    break
            }
        } else {
            trainingData = split
            pruningData = null
        }

        // merge classifier for the training data
        def builder = new BuildTreeAndGetBoxSet(results)

        // save the bounding box before possible discretization
        def nonDiscretizedBoundingBox = Tools.getBoundingBox(trainingData)


        // AA 8.07.2012-discretize: todo: if discretization is done as a first step (in ExpAllCode), then remove this block!
        int discretize = results.getInt("Pdisc")
        if (discretize > 0) {
            def filter = (discretize == 1) ? new weka.filters.unsupervised.attribute.Discretize() : new weka.filters.supervised.attribute.Discretize()
            filter.setInputFormat(trainingData)
            trainingData = Filter.useFilter(trainingData, filter)
        }


        def size = trainingData.numInstances()

        // build the classifier on a subset and add the result to the classifier of the overall trainings-data
        BoxSet boxSet = builder.buildCubes(trainingData)
        // AA 5.07.2012 update the boundingBox with the non-discretized version
        // todo: check, whether we need repeat this in other classifiers
        boxSet.boundingBox = nonDiscretizedBoundingBox
        def t1 = tic()

        if (recordStatistics) {

            synchronized (results) {
                (results.cubesBuilt as List<Integer>) << boxSet.size()
            }
            classifiers.mapreduce.MergedTreeClassifierMapper.log.info("""|box-counting|:
                        ${this.class.getSimpleName()}-${id} finished execution on ${size} out of ${split.numInstances()}
                        instances, resulting in ${boxSet.size()} cubes,
                        after building the underlying tree in ${toDiffString(t0, t1)}.""")
        }

        // dividing the id by 2 ensures two collections to be merged
        return new MergedTreeClassifier.ProcessingResult((int) (id / 2), 0, boxSet, pruningData, "" + id, id)
    }
}

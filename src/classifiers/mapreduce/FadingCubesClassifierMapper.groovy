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

import edu.pvs.batchrunner.ExperimentResult
import groovy.util.logging.Log
import java.util.concurrent.Callable
import weka.core.Instances
import weka.filters.Filter

import cubes.BuildTreeAndGetBoxSet
import experiment.PerfUtils
import experiment.ExperimentResultSingletonHolder

/**
 * Created by IntelliJ IDEA.
 * User: flangner
 * Date: 20.02.12
 * Time: 15:59
 * To change this template use File | Settings | File Templates.
 */
@Log @Typed
class FadingCubesClassifierMapper implements Callable<Collection<ClassCube>[]>, PerfUtils {

    private final int id
    private final boolean recordStatistics
    private final ExperimentResult results
    private final BuildTreeAndGetBoxSet builder

    private Instances split

    FadingCubesClassifierMapper(Instances split, int id, BuildTreeAndGetBoxSet builder, boolean recordStatistics) {

        // parameters that might be found at ExperimentResult
        // they are initialized with default values
        this.results = ExperimentResultSingletonHolder.getInstance()

        this.split = split
        this.id = id
        this.builder = builder
        this.recordStatistics = recordStatistics
    }

    @Override
    Collection<ClassCube>[] call() {
        def t0 = tic()

        // merge classifier for the training data
        def builder = new BuildTreeAndGetBoxSet(results)

        def discretize = Integer.parseInt(results.Pdisc as String)
        if (discretize > 0) {
            def filter = (discretize == 1) ? new weka.filters.unsupervised.attribute.Discretize() : new weka.filters.supervised.attribute.Discretize()
            filter.setInputFormat(split)
            split = Filter.useFilter(split, filter)
        }


        def size = split.numInstances()

        // build the classifier on a subset and add the result to the classifier of the overall trainings-data
        def classCubes = builder.buildCubes(split)
        def t1 = tic()

        if (recordStatistics) {
            synchronized (results) {
                (results.cubesBuilt as List<Integer>) << classCubes.size()
            }
            classifiers.mapreduce.FadingCubesClassifierMapper.log.info("${this.class.getSimpleName()}-${id} finished execution on ${size} instances in ${toDiffString(t0, t1)}.")
        }

        Collection<ClassCube>[] result = new Collection<ClassCube>[1]
        result[0] = classCubes
        return result
    }
}

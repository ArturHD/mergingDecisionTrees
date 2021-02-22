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
import weka.core.Attribute
import weka.core.Instance
import classifiers.FadingCubesClassifier
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
class FadingCubesClassifierWorker implements Callable<double[]>, PerfUtils {

    private final int id
    private final int k
    private final boolean recordStatistics
    private final ExperimentResult results
    private final Collection<ClassCube> cubes

    Instance sample

    FadingCubesClassifierWorker(int id, Collection<ClassCube> cubes, boolean recordStatistics) {

        // parameters that might be found at ExperimentResult
        // they are initialized with default values
        this.results = ExperimentResultSingletonHolder.getInstance()
        this.id = id
        this.cubes = cubes
        this.recordStatistics = recordStatistics
    }

    @Override
    double[] call() {

        double[] result = new double[sample.numClasses()];

        switch (sample.classAttribute().type()) {

            case Attribute.NOMINAL:

                // find the cube that covers the sample
                for (ClassCube cube: cubes) {
                    if (cube.isInsideCube(sample)) {

                        double[] distribution = cube.classData.classProbDistribution
                        double cubeWeight = FadingCubesClassifier.calculateSampleInCubeProbability(sample, cube)
                        assert distribution.length == sample.numClasses(), "Distribution vector from tree has different length (${distribution.length}) than supplied sample (${sample.numClasses()})"

                        // calculate the weighted probability distribution for the class-Attribute
                        for (int j = 0; j < result.length; j++) {

                            result[j] += distribution[j] * cubeWeight
                        }
                        break
                    }
                }
                break

            case Attribute.NUMERIC:

                throw new InternalError("FadingCubesClassifier does not support classification for numeric classes.")
        }

        return result
    }
}

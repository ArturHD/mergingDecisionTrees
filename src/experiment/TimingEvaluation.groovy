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

package experiment

import edu.pvs.batchrunner.ExperimentResult
import java.util.concurrent.Executors
import weka.classifiers.Classifier
import weka.classifiers.Evaluation
import weka.core.Instances
import weka.core.Range

/**
 * Performs a cross-validation of a classifier (model) while recording needed time and memory usage
 */

@Typed
class TimingEvaluation extends Evaluation implements PerfUtils {

    private ExperimentResult experimentResult

    TimingEvaluation(weka.core.Instances data, ExperimentResult experimentResult) {
        super(data)
        this.experimentResult = experimentResult
    }

    @Override
    void crossValidateModel(Classifier classifier, Instances data, int numFolds, Random random, Object... forPredictionsPrinting) {
        // Make a copy of the data we can reorder
        data = new Instances(data);
        data.randomize(random);
        if (data.classAttribute().isNominal()) {
            data.stratify(numFolds);
        }

        // We assume that the first element is a StringBuffer, the second a Range (attributes
        // to output) and the third a Boolean (whether or not to output a distribution instead
        // of just a classification)
        if (forPredictionsPrinting.length > 0) {
            // print the header first
            StringBuffer buff = (StringBuffer) forPredictionsPrinting[0];
            Range attsToOutput = (Range) forPredictionsPrinting[1];
            boolean printDist = ((Boolean) forPredictionsPrinting[2]).booleanValue();
            printClassificationsHeader(data, attsToOutput, printDist, buff);
        }

        def executor = Executors.newSingleThreadExecutor()
        experimentResult.with {
            classifierBuildTime = new ArrayList<Integer>(numFolds)
            classifierBuildPeakMemUsage = new ArrayList<Integer>(numFolds)
            classifierClassificationTime = new ArrayList<Integer>(numFolds)
            classifierClassificationPeakMemUsage = new ArrayList<Integer>(numFolds)
        }
        // Do the folds
        for (int i = 0; i < numFolds; i++) {
            Instances train = data.trainCV(numFolds, i, random);
            setPriors(train);
            Classifier copiedClassifier = Classifier.makeCopy(classifier);

            System.gc()
            def t0 = tic()

            def memSampler = new MemSampler()
            def mMaxFuture = executor.submit(memSampler)

            copiedClassifier.buildClassifier(train);

            def t1 = tic()

            memSampler.finish = true
            def m_max = mMaxFuture.get()

            (experimentResult.classifierBuildTime as List<Integer>) << timeDiff(t0, t1)
            (experimentResult.classifierBuildPeakMemUsage as List<Integer>) << m_max - t0.second


            Instances test = data.testCV(numFolds, i);

            System.gc()
            def t2 = tic()

            memSampler = new MemSampler()
            mMaxFuture = executor.submit(memSampler)

            evaluateModel(copiedClassifier, test, forPredictionsPrinting);

            def t3 = tic()

            memSampler.finish = true
            m_max = mMaxFuture.get()

            (experimentResult.classifierClassificationTime as List<Integer>) << timeDiff(t2, t3)
            (experimentResult.classifierClassificationPeakMemUsage as List<Integer>) << m_max - t2.second

        }
        m_NumFolds = numFolds;
        experimentResult.with {
            updateMaxAveFromList "classifierBuildTime"
            updateMaxAveFromList "classifierBuildPeakMemUsage"
            updateMaxAveFromList "classifierClassificationTime"
            updateMaxAveFromList "classifierClassificationPeakMemUsage"
        }
    }

}

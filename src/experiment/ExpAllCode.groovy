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

import edu.pvs.batchrunner.ExperimentCode
import edu.pvs.batchrunner.ExperimentResult
import groovy.util.logging.Log
import weka.classifiers.Classifier
import weka.classifiers.Evaluation
import weka.classifiers.trees.J48
import weka.classifiers.trees.RandomForest
import classifiers.*
import weka.core.Instances
import weka.core.Instance
import weka.classifiers.meta.FilteredClassifier

/**
 * Created by: Artur Andrzejak 
 * Date: 05.02.12
 * The run method of this class is called in each iteration of the batchrunner (with new param combination).
 * So this is the top-level code called in each single experiment (called by batchrunner).
 */
@Log @Typed
class ExpAllCode implements ExperimentCode, PerfUtils {

    Map<String, Object> settings = [:]

    @Override
    public void init(ExperimentResult newExperimentResult, Map<String, Object> settings) {
        System.out.println("\n ExperimentCodeTest.init() called");
        this.settings = settings
    }


    @Override
    public int run(ExperimentResult experimentResult) {
        log.info "\n==============================\n *** Running experiment ${experimentResult.toString()}"
        String datasetFile = "data/arff/" + experimentResult["dataset"] as String
        log.info("Loading $datasetFile")
        def data = Tools.loadArff(datasetFile)


        Classifier tree

        String treeType = experimentResult.getString "treeType"
        switch (treeType) {
            case "mtc": tree = new MergedTreeClassifier(); break;   // merging tree classifier
            case "fcc": tree = new FadingCubesClassifier(); break;  // fading cubes classifier (?), (probably not working)
            case "tc": tree = new VotingTreeClassifier(); break;    // voting classifier using a box-set + tree built on it
            case "opc": tree = new OnePassClassifier(); break;
            case "rsc": tree = new RandomSamplingClassifier(); break;
            case "vc": tree = new VotingClassifier(); break;        // general voting classifier, can use any classifier for a single "vote"

            case "j48": tree = new J48(); break;                    // std J48 tree
            case "rf": tree = new RandomForest(); break;            // std random forest
            case "cd": tree = new ConceptDriftClassifier(); break;  // concept drift experiment (probably not working)
        }

        // 0= no discretisation, 1 = unsupervised, 2 = supervised; -10 = "global discretisation"
        int discretize = experimentResult.getInt "Pdisc"

        // it does the discretisation internally in the classifiers
        if (discretize > 0)
            switch (treeType) {
/*
                case "tc":
                case "mtc":
                case "fcc":
                case "vc":
                    // discretize internally
                    break
*/
                case "j48":
                case "rf":
                case "cd":
                    // Caution: this performs GLOBAL discretisation!
                    def filter = (discretize == 1) ? new weka.filters.unsupervised.attribute.Discretize() : new weka.filters.supervised.attribute.Discretize()
                    def discretizedTree = new FilteredClassifier()
                    discretizedTree.setFilter(filter)
                    discretizedTree.setClassifier(tree)
                    tree = discretizedTree
            }



        // do GLOBAL discretisation (formely problem AA 8.07.2012-discretize)
        if (discretize == -10) {
            Instances tmp = Tools.discretizeInstances(data, true)

            data = new Instances(data,0)    // remove all instances but keep "numeric" as type of attributes
            for (int i = 0; i < tmp.numInstances(); i++) {
                data.add(new Instance(tmp.instance(i)))
            }
        }


        // Initialize the values of the results + statistics in the ER
        experimentResult.cubesBuilt = []
        experimentResult.cubeCountRatioAfterMerging = []
        experimentResult.cubeCompressionRatio = []
        experimentResult.finalCubeCount = []
        experimentResult.cutBoxesAtBuildTree = []
        experimentResult.treeDepth = []
        experimentResult.fold = 0
        experimentResult.conflictBoxes = 0

        // Set the current ER instance into a static wrapper to retrieve it in all classes w/out "param passing"
        ExperimentResultSingletonHolder.setInstance(experimentResult)

        log.info("Evaluating ${tree.class.simpleName}")

        // This class performs cross-validation
        Evaluation xValidator = new TimingEvaluation(data, experimentResult)
        if (treeType.equals("cd") ) {
            // for ConceptDriftClassifier: just build the model
            tree.buildClassifier(data)
        } else {
            // otherwise make cross-validation experiment
            int numFolds = experimentResult.getInt "NumFolds"
            xValidator.crossValidateModel(tree, data, numFolds, new Random(1))
        }

        experimentResult.with {

            if (!treeType.equals("cd")) {
                confusionMatrix = xValidator.confusionMatrix()
                pctCorrect = xValidator.pctCorrect()
                pctIncorrect = xValidator.pctIncorrect()
                pctUnclassified = xValidator.pctUnclassified()
            }

            def numTruePositives = xValidator.numTruePositives(0)
            def numFalsePositives = xValidator.numFalsePositives(0)
            def numFalseNegatives = xValidator.numFalseNegatives(0)
            def numTrueNegatives = xValidator.numTrueNegatives(0)

            precisionClass0 = numTruePositives / (numTruePositives + numFalsePositives) * 100
            recallClass0 = numTruePositives / (numTruePositives + numFalseNegatives) * 100
            specificityClass0 = numTrueNegatives / (numTrueNegatives + numFalsePositives) * 100
            accuracyClass0 = (numTruePositives + numTrueNegatives) / (numTruePositives + numTrueNegatives + numFalsePositives + numFalseNegatives) * 100
            ROCAUCClass0 = xValidator.areaUnderROC(0)

            updateMaxAveFromList "cubesBuilt"
            updateMaxAveFromList "cubeCountRatioAfterMerging"
            updateMaxAveFromList "cubeCompressionRatio"
            updateMaxAveFromList "finalCubeCount"
            updateMaxAveFromList "cutBoxesAtBuildTree"
            updateMaxAveFromList "treeDepth"

            // some info concerning the data not the classifier
            numClasses = data.numClasses()
            numAttributes = data.numAttributes()
            numInstances = data.numInstances()
            assert (treeType.equals("cd") || data.numInstances() == xValidator.numInstances())

            appendToSummary("results")
        }

        if (new Boolean(this.settings.saveToFile as String))
            experimentResult.saveToFile("results")

        log.info("Result was $experimentResult")

        def tg = tic()
        log.info("Sleeping 0.2s, waiting for the garbage man ...")
        System.gc()
        Thread.sleep(200)
        def tag = tic()
        log.info("Got ${toDiffString(tg, tag)}!")

        return 1;
    }
}



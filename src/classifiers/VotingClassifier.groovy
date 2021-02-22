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

import weka.classifiers.meta.Vote
import weka.core.Instances
import edu.pvs.batchrunner.ExperimentResult
import experiment.ExperimentResultSingletonHolder

import static experiment.Tools.createKSplits
import weka.classifiers.Classifier

import static experiment.Tools.getClassifierInstanceFromClassName
import static experiment.Tools.loadArff
import experiment.Tools

/**
 * Created by: Artur Andrzejak 
 * Date: 15.07.12
 * Time: 18:09
 * A (general) voting classifier which splits training set into k fragments,
 * builds a classifier (of specified type) on each fragment, and uses a voting mechanism (default: probability average)
 * to classify.
 * Uses method distributionForInstance() inherited from Vote to perform classification.
 * All trained classifiers are stored in inherited field Classifier[] m_Classifiers
 *
 * The default voting method is the AVERAGE_RULE, -  "Average of Probabilities" (see Vote)
 */
class VotingClassifier extends Vote{

    /**
     * Splits training set into k fragments and builds a classifier (of specified type) on each fragment
     * If data == null the classifier will try to load instances from ARFF files.
     *
     * @param data
     */
    @Override
    void buildClassifier(Instances data) {


        // parameters that might be found at ExperimentResult - if not, they are initialized with default values
        final ExperimentResult results = ExperimentResultSingletonHolder.getInstance()
        final int k = (results) ? results.getInt("Ek") : 1
        int discretize = (results) ? results.getInt ("Pdisc") : 2
        // parameter for the type of the final classifier
        final String classifierType = (results && results.FinalClassif) ? results.getString("FinalClassif") : "J48"
        // todo: introduce a parameter to determine the voting rule (not important)
        m_CombinationRule = weka.classifiers.meta.Vote.AVERAGE_RULE     // default voting method is Average of Probabilities

        // Possibly reduce size for each fragment by "reusing" the parameter Ncentro
        // If Ncentro is <= 1.0 then it is the fraction of the # of ALL training instances (before splits)
        //    else: Ncentro is the absolute number of centroids
        final double maxFragmentSizeDouble = (results) ? results.getDouble("Ncentro") : 1000
        int maxFragmentSize

        if (maxFragmentSizeDouble <= 1.0) {
            // fractional parameter, multiply with # of all instances
            int numInstances = data.numInstances()
            maxFragmentSize = Math.floor(numInstances * maxFragmentSizeDouble)

        } else {
            maxFragmentSize = (int) maxFragmentSizeDouble
        }



        // split the data
        final List<Instances> splits = createKSplits(k, data, results)

        // build the classifiers
        m_Classifiers = new Classifier[k]
        for (int i = 0; i < k; i++) {

            // get data and possibly discretize
            Instances split = (splits) ? splits.get(i) : loadArff("split${i}")
            if (discretize > 0) {
                def supervised = discretize == 2
                split = Tools.discretizeInstances(split, supervised)
            }

            int numInstances = split.numInstances()
            if (numInstances > maxFragmentSize) {
                // reduce input fragment size
                def resampled = experiment.Tools.getResampledSubset(split, 100)
                double percentageToRemove = ((double)100*(numInstances - maxFragmentSize))/((double) numInstances)
                // now get a part of the resampled
                split = experiment.Tools.removePercentageOfInstances(resampled,percentageToRemove)
            }

            Classifier classifier = getClassifierInstanceFromClassName(classifierType)
            classifier.buildClassifier(split)
            m_Classifiers[i] = classifier
        }

        //todo: get the # of nodes of each tree for tree-like classifiers

    }



}

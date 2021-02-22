package weka.classifiers.trees

import weka.classifiers.Classifier
import weka.classifiers.GetAccessToClassifiersInIteratedSingleClassifierEnhancer

@Typed
class GetAccessToClassifiersInRandomForest {
    static Classifier[] getClassifiers(RandomForest rf) {
        GetAccessToClassifiersInIteratedSingleClassifierEnhancer.getClassifiers(rf.m_bagger)
    }
}

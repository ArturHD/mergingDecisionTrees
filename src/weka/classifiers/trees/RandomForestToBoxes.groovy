package weka.classifiers.trees

import cubes.ClassCube
import weka.classifiers.Classifier
import weka.classifiers.GetAccessToClassifiersInIteratedSingleClassifierEnhancer

@Typed
class RandomForestToBoxes {
    static List<List<ClassCube>> forestToBoxes(RandomForest rf) {
        Classifier[] classifiers = GetAccessToClassifiersInIteratedSingleClassifierEnhancer.getClassifiers(rf.m_bagger)
        List<List<ClassCube>> result = []
        classifiers.each {
            RandomTree rt = it as RandomTree
            result << RandomTreeToBoxes.treeToBoxes(rt)
        }
        return result
    }
}

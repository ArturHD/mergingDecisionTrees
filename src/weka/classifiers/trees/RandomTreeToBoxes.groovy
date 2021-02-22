package weka.classifiers.trees

import cubes.ClassCube
import weka.core.Utils
import weka.core.Instances
import experiment.Tools
import cubes.BoxSet

@Typed
class RandomTreeToBoxes extends cubes.TreeToBoxes<RandomTree> {

    @Override
    List<RandomTree> getChildren(RandomTree node) {
        node.m_Successors as List
    }

    @Override
    boolean isLeaf(RandomTree node) {
        return node.m_Attribute == -1
    }

    @Override
    int getClassValue(RandomTree t) {
        def maxIndex = Utils.maxIndex(t.m_ClassDistribution);
        return maxIndex
    }

    @Override
    double getConfidence(RandomTree t) {
        def sum = Utils.sum(t.m_ClassDistribution);
        def maxIndex = Utils.maxIndex(t.m_ClassDistribution);
        def maxCount = t.m_ClassDistribution[maxIndex];
        def confidence = maxCount / sum
        return confidence
    }

    @Override
    double[] getClassProbDistribution(RandomTree t) {
        def numClasses = t.m_Info.numClasses()
        def result = new double[numClasses]
        def sum = Utils.sum(t.m_ClassDistribution)
        for (int i in 0..<numClasses)
            result[i] = t.m_ClassDistribution[i] / sum
        return result
    }

    @Override
    String getClassName(RandomTree t) {
        def maxIndex = Utils.maxIndex(t.m_ClassDistribution);
        return t.m_Info.classAttribute().value(maxIndex)
    }

    @Override
    int getAttributeIndex(RandomTree t) {
        t.m_Attribute
    }

    @Override
    String getAttributeName(RandomTree t) {
        t.m_Info.attribute(getAttributeIndex(t)).name()
    }

    @Override
    double getSplitPoint(RandomTree t) {
        t.m_SplitPoint
    }

    @Override
    boolean isNumericBinarySplit(RandomTree t) {
        getSplitPoint(t) != Double.NaN
    }

    @Override
    boolean isParsebleNumericRange(RandomTree t, int childIndex) {
        return false  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    Pair<Double, Double> getBounds(RandomTree t, int childIndex) {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    boolean isLesserChild(RandomTree t) {
        index[t].intValue() == 0   // SZ: Implementation detail, see RandomTree.toString(index)
    }

    @Override
    int getNumDimensions(RandomTree t) {
        t.m_Info.numAttributes() - 1 // AA: -1 as one (usually last) attribute is class
    }

    @Override
    int getNumInstances(RandomTree t) {
        Utils.sum(t.m_ClassDistribution)
    }

    static BoxSet treeToBoxes(RandomTree tree) {
        def rttb = new RandomTreeToBoxes()
        rttb.getReverseDAG(tree)
        BoxSet boxes = rttb.getBoxes()
        return boxes
    }
}

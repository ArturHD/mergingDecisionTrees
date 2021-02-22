package weka.classifiers.trees.j48

import cubes.ClassCube
import weka.classifiers.trees.J48
import weka.core.Instances
import cubes.BoxSet
import experiment.Tools

/**
 * Created by IntelliJ IDEA.
 * User: Artur Andrzejak
 * Date: 15.01.12
 * Time: 20:32
 * Procedures to transform a J48 tree into a collection of d-cubes
 */
@Typed public class J48toCubes extends cubes.TreeToBoxes<ClassifierTree> {

    @Override
    List<ClassifierTree> getChildren(ClassifierTree t) {
        t.m_sons as List
    }

    @Override
    boolean isLeaf(ClassifierTree t) {
        t.m_isLeaf
    }

    @Override
    int getClassValue(ClassifierTree t) {
        t.m_localModel.m_distribution.maxClass(0) // TODO Index?
    }

    @Override
    double getConfidence(ClassifierTree t) {
        def total = t.m_localModel.m_distribution.total()
        if (total > 0)
            t.m_localModel.m_distribution.numCorrect() / total
        else
            0.0
    }

    @Override
    double[] getClassProbDistribution(ClassifierTree t) {
        def numClasses = t.m_localModel.m_distribution.numClasses()
        def result = new double[numClasses]

        // see ClassifierTree.getProbs
        def distribution = t.m_isEmpty ? fathers[t].m_localModel.distribution() : t.m_localModel.distribution()

        for (int i in 0..<numClasses)
            result[i] = distribution.prob(i)

        return result
    }

    @Override
    String getClassName(ClassifierTree t) {
        t.m_train.classAttribute().value(getClassValue(t))
    }

    @Override
    int getAttributeIndex(ClassifierTree t) {
        t.m_localModel.m_attIndex
    }

    @Override
    String getAttributeName(ClassifierTree t) {
        t.m_train.attribute(getAttributeIndex(t)).name()
    }

    @Override
    double getSplitPoint(ClassifierTree t) {
        t.m_localModel.m_splitPoint
    }

    @Override
    boolean isNumericBinarySplit(ClassifierTree t) {
        t.m_train.attribute(t.m_localModel.m_attIndex).isNumeric() && (t.m_sons.length == 2) // TODO is this all?
    }

    @Override
    boolean isParsebleNumericRange(ClassifierTree t, int childIndex) {
        def nominalValue = t.m_train.attribute(t.m_localModel.m_attIndex).value(childIndex)
        def rangeMatch = rangePattern.matcher(nominalValue)
        rangeMatch.matches() && (t.m_localModel.m_splitPoint == Double.MAX_VALUE)
    }

    @Override
    Pair<Double, Double> getBounds(ClassifierTree t, int childIndex) {
        def nominalValue = t.m_train.attribute(t.m_localModel.m_attIndex).value(childIndex)
        if (nominalValue == "'All'")
            return null
        else {
            def rangeMatch = rangePattern.matcher(nominalValue)
            if (rangeMatch.matches()) {
                def result = new Pair<Double, Double>()

                String lower = rangeMatch.group(2)
                if (lower.equalsIgnoreCase("-inf"))
                    result.first = Double.NEGATIVE_INFINITY
                else
                    result.first = Double.valueOf(lower)

                String upper = rangeMatch.group(5)
                if (upper.equalsIgnoreCase("inf"))
                    result.second = Double.POSITIVE_INFINITY
                else
                    result.second = Double.valueOf(upper)

                return result
            } else
                throw new IllegalStateException()
        }
    }


    static List<ClassCube> treeToBoxes(J48 tree) {
        def j48ToCubes = new J48toCubes()
        j48ToCubes.getReverseDAG(tree.getRoot())
        BoxSet boxes = j48ToCubes.getBoxes()
        return boxes
    }

    @Override
    boolean isLesserChild(ClassifierTree node) {
        return index[node].intValue() == 0   // AA: why? // SZ: Implementation detail, see (Bin)C45Split#rightSide()
    }

    @Override
    int getNumDimensions(ClassifierTree t) {
        t.m_train.numAttributes() - 1 // AA: -1 as one (usually last) attribute is class
    }

    @Override
    int getNumInstances(ClassifierTree t) {
        return t.m_localModel.m_distribution.total()
    }
}

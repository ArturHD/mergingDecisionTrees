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

package WekaGroovyExamples
/*
 *    This program is free software you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

import weka.classifiers.Classifier

import weka.core.Attribute
import weka.core.Capabilities
import weka.core.Instance
import weka.core.Instances
import weka.core.RevisionUtils
import weka.core.Utils
import weka.core.WeightedInstancesHandler
import weka.core.Capabilities.Capability

/**
 <!-- globalinfo-start -->
 * Class for building and using a 0-R classifier. Predicts the mean (for a numeric class) or the mode (for a nominal class).
 * <p/>
 <!-- globalinfo-end -->
 *
 <!-- options-start -->
 * Valid options are: <p/>
 * 
 * <pre> -D
 *  If set, classifier is run in debug mode and
 *  may output additional info to the console</pre>
 * 
 <!-- options-end -->
 *
 * @author Eibe Frank (original ZeroR Java code)
 * @author FracPete (fracpete at waikato dot ac dot nz)
 * @version $Revision$
 */
class GeroR 
  extends Classifier 
  implements WeightedInstancesHandler {
  
  /** The class value 0R predicts. */
  protected double m_ClassValue

  /** The number of instances in each class (null if class numeric). */
  protected double [] m_Counts
  
  /** The class attribute. */
  protected Attribute m_Class
    
  /**
   * Returns a string describing classifier
   * @return a description suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "Class for building and using a 0-R classifier. Predicts the mean " 
      + "(for a numeric class) or the mode (for a nominal class)."	    
  }

  /**
   * Returns default capabilities of the classifier.
   *
   * @return      the capabilities of this classifier
   */
  public Capabilities getCapabilities() {
    Capabilities result = super.getCapabilities()

    // attributes
    result.enable(Capability.NOMINAL_ATTRIBUTES)
    result.enable(Capability.NUMERIC_ATTRIBUTES)
    result.enable(Capability.DATE_ATTRIBUTES)
    result.enable(Capability.STRING_ATTRIBUTES)
    result.enable(Capability.RELATIONAL_ATTRIBUTES)
    result.enable(Capability.MISSING_VALUES)

    // class
    result.enable(Capability.NOMINAL_CLASS)
    result.enable(Capability.NUMERIC_CLASS)
    result.enable(Capability.DATE_CLASS)
    result.enable(Capability.MISSING_CLASS_VALUES)

    // instances
    result.setMinimumNumberInstances(0)
    
    return result
  }

  /**
   * Generates the classifier.
   *
   * @param instances set of instances serving as training data 
   * @throws Exception if the classifier has not been generated successfully
   */
  public void buildClassifier(Instances instances) throws Exception {
    // can classifier handle the data?
    getCapabilities().testWithFail(instances)

    // remove instances with missing class
    instances = new Instances(instances)
    instances.deleteWithMissingClass()
    
    double sumOfWeights = 0

    m_Class = instances.classAttribute()
    m_ClassValue = 0
    switch (instances.classAttribute().type()) {
      case Attribute.NUMERIC:
        m_Counts = null
        break
      case Attribute.NOMINAL:
        m_Counts = new double [instances.numClasses()]
        for (int i = 0; i < m_Counts.length; i++) {
          m_Counts[i] = 1
        }
        sumOfWeights = instances.numClasses()
        break
    }
    Enumeration enu = instances.enumerateInstances()
    while (enu.hasMoreElements()) {
      Instance instance = (Instance) enu.nextElement()
      if (!instance.classIsMissing()) {
	if (instances.classAttribute().isNominal()) {
	  m_Counts[(int)instance.classValue()] += instance.weight()
	} else {
	  m_ClassValue += instance.weight() * instance.classValue()
	}
	sumOfWeights += instance.weight()
      }
    }
    if (instances.classAttribute().isNumeric()) {
      if (Utils.gr(sumOfWeights, 0)) {
	m_ClassValue /= sumOfWeights
      }
    } else {
      m_ClassValue = Utils.maxIndex(m_Counts)
      Utils.normalize(m_Counts, sumOfWeights)
    }
  }

  /**
   * Classifies a given instance.
   *
   * @param instance the instance to be classified
   * @return index of the predicted class
   */
  public double classifyInstance(Instance instance) {

    return m_ClassValue
  }

  /**
   * Calculates the class membership probabilities for the given test instance.
   *
   * @param instance the instance to be classified
   * @return predicted class probability distribution
   * @throws Exception if class is numeric
   */
  public double [] distributionForInstance(Instance instance) 
       throws Exception {
	 
    if (m_Counts == null) {
      double[] result = new double[1]
      result[0] = m_ClassValue
      return result
    } else {
      return (double []) m_Counts.clone()
    }
  }
 
  /**
   * Returns a description of the classifier.
   *
   * @return a description of the classifier as a string.
   */
  public String toString() {
    if (m_Class ==  null) {
      return "GeroR: No model built yet."
    }
    if (m_Counts == null) {
      return "GeroR predicts class value: " + m_ClassValue
    } else {
      return "GeroR predicts class value: " + m_Class.value((int) m_ClassValue)
    }
  }
  
  /**
   * Returns the revision string.
   * 
   * @return		the revision
   */
  public String getRevision() {
    return RevisionUtils.extract("\$Revision: 1.19 \$")
  }

  /**
   * Main method for executing this classifier.
   *
   * @param argv the options
   */
  public static void main(String[] argv) {
    runClassifier(new GeroR(), argv)
  }
}

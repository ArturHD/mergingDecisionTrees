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

package cubes

import weka.core.Instance
import weka.core.Instances
import weka.filters.Filter
import weka.filters.UnsupervisedFilter

/**
 * Filter to remove instances that reside inside the boundaries of a given Cube
 *
 * Created by IntelliJ IDEA.
 * User: flangner
 * Date: 07.02.12
 * Time: 15:58
 * To change this template use File | Settings | File Templates.
 */
@Typed
class RemoveCube extends Filter implements UnsupervisedFilter {

    private Cube m_cube = null

    /** Indicates if inverse of selection is to be output. */
    private boolean m_Inverse = false

    /**
     * Input an instance for filtering. Ordinarily the instance is processed
     * and made available for output immediately. Some filters require all
     * instances be read before producing output.
     *
     * @param instance the input instance
     * @return true if the filtered instance may now be
     * collected with output().
     * @throws IllegalStateException if no input format has been set.
     */
    public boolean input(Instance instance) {
        if (getInputFormat() == null) {
            throw new IllegalStateException("No input instance format defined");
        }

        if (m_NewBatch) {
            resetQueue();
            m_NewBatch = false;
        }

        if (isFirstBatchDone()) {
            push(instance);
            return true;
        }
        else {
            bufferInput(instance);
            return false;
        }
    }

    /**
     * Signify that this batch of input to the filter is
     * finished. Output() may now be called to retrieve the filtered
     * instances.
     *
     * @return true if there are instances pending output
     * @throws IllegalStateException if no input structure has been defined
     */
    public boolean batchFinished() {

        if (getInputFormat() == null) {

            throw new IllegalStateException("No input instance format defined")
        } else if (m_cube == null) {

            throw new IllegalStateException("No filtering Cube defined.")
        }

        // Push instances for output into output queue
        def toFilter = getInputFormat()
        for (Instance instance in toFilter.enumerateInstances()) {
            if (m_Inverse == cube.isInsideCube(instance)) {

                push(instance)
            }
        }
        flushInput();

        m_NewBatch = true;
        m_FirstBatchDone = true;

        return (numPendingOutput() != 0);
    }

/*
 * getter/setter
 */

    void setCube(Cube cube) {

        this.m_cube = cube
    }

    Cube getCube() {

        return m_cube
    }

    /**
     * Sets the format of the input instances.
     *
     * @param instanceInfo an Instances object containing the input instance
     * structure (any instances contained in the object are ignored - only the
     * structure is required).
     * @return true because outputFormat can be collected immediately
     * @throws Exception if the input format can't be set successfully
     */
    public boolean setInputFormat(Instances instanceInfo) throws Exception {

        super.setInputFormat(instanceInfo);
        setOutputFormat(instanceInfo);
        return true;
    }

    /**
     * Gets if selection is to be inverted.
     *
     * @return true if the selection is to be inverted
     */
    public boolean getInvertSelection() {

        return m_Inverse;
    }

    /**
     * Sets if selection is to be inverted.
     *
     * @param inverse true if inversion is to be performed
     */
    public void setInvertSelection(boolean inverse) {

        m_Inverse = inverse;
    }
}

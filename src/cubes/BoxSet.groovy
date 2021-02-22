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

import groovy.util.logging.Log

/**
 * Created by: Artur Andrzejak 
 * Date: 24.06.12
 * Time: 12:27
 * Extends Collection<ClassCube> to contain additional infos, e.g. the "bounding box" derived from weka-instances
 */
@Typed @Log
class BoxSet extends ArrayList<ClassCube>{

    // The bounding box of box collection obtained from Weka-Instances, i.e. for each dim the min and max value of attribute at dim
    Cube boundingBox

    // ------------ Creators and constructors ------------
    static BoxSet create(int initialCapacity = 0) {
        def result
        if (initialCapacity <= 0)
            result =  new BoxSet(initialCapacity)
        else
            result = new BoxSet()
        return result
    }

    // Default constructor
    def BoxSet()  {
        super()
    }

    // A constructor with initialCapacity
    def BoxSet(int initialCapacity) {
        super(initialCapacity)
    }

    // A constructor from collection, optionally copies the "additional" data
    def BoxSet(Collection<ClassCube> _boxesCollection) {
        super(_boxesCollection)
        copyAdditionalData(_boxesCollection)
    }

    private void copyAdditionalData(Collection<ClassCube> _boxesCollection) {
        if (_boxesCollection instanceof BoxSet) {
            this.boundingBox = ((BoxSet) _boxesCollection).boundingBox
        }
    }


    // --------------- Methods --------------

    // Adds another BoxSet to this container, and if it was not null,
    // performs intersections of boxes and stores only the intersections
    // It assumes that each et of boxes partitions the space completely!
    // - todo: make mergingBoxSetsViaIntersections consider bounding boxes !!!!
    void mergeBoxSetsViaIntersections(BoxSet newBoxSet) {

        if (size() <= 0) {
            // This is the first time we add to this container - just copy the parameter contents
            this.addAll(newBoxSet)
            copyAdditionalData(newBoxSet)
        } else {
            // There are already cubes in this container; intersect them with the newCollection and store the intersections
            Set<Pair<Cube, Cube>> intersectingPairs = new CubeIntersectionFinder().findIntersections(this, newBoxSet)
            BoxSet resultingBoxSet = new BoxSet(intersectingPairs.size())
            for (pair in intersectingPairs) {
                def cubeFromContainer = (ClassCube) pair.first
                def newCube = (ClassCube) pair.second
                resultingBoxSet << cubeFromContainer.getIntersection(newCube)
            }
            assert resultingBoxSet.size() == intersectingPairs.size()
            // now replace the contents of this object by resultingBoxSet
            this.clear()
            addAll(resultingBoxSet)

            // compute the "union" of the bounding boxes
            assert boundingBox != null, "At intersecting boxSets: encountered boxSet without bounding box info (null field)"
            this.boundingBox = boundingBox.getEnclosing(newBoxSet.boundingBox)

/*
            if (this.boundingBox != null)
                this.boundingBox = boundingBox.getEnclosing(newBoxSet.boundingBox)
            else {
                log.warning "At intersecting: Encountered boxSet without valid bounding box!"
            }
*/

            /*
            // count the conflicting cubes
            int sizeColA = boxSet.size()    // move before intersections, if used
            int sizeColB = newBoxSet.size()
            int numConflicts = 0
            for (cube in boxSet)
                if (cube.classData.hasConflict)
                    numConflicts++
            log.info "At intersecting: #conflicts = $numConflicts; colA.size, colB.size, merged.size = $sizeColA, $sizeColB, ${boxSet.size()}"
            */
        }
    }

}

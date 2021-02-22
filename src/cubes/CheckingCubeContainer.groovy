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

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import groovy.util.logging.Log

/**
 * User: Artur Andrzejak 
 * Date: 25.01.12
 * Time: 18:03
 * Contains a running container (of both non-conflicting and conflicting) cubes.
 * Provides routines to add new collection of cubes and handling the intersections of existing and new cubes
 * This container assumes  that each set of boxes partitions the space completely - as a set of boxes derived from a decision tree does
 * This is a container for "old code", not used anymore
 */
@Log @Typed @Deprecated
class CheckingCubeContainer {

    // Maps ClassCube.super() (i.e. Cube "contained" in a ClassCube) to ClassCube, and vice versa
    BiMap<GeometricCube, ClassCube> geom2classMap = HashBiMap.create()
    BiMap<ClassCube, GeometricCube> class2geomMap = geom2classMap.inverse()

    // Some statistics
    int numTotalIntersections = 0
    int numNewIntersections


    void mergeBoxSetsViaIntersections(BoxSet newBoxSet) {

        // 1. Find all intersecting pairs
        def containerClassCubes = geom2classMap.values()
        Set<Pair<Cube, Cube>> intersectingPairs = new CubeIntersectionFinder().findIntersections(containerClassCubes, newBoxSet)
        log.info("Found ${intersectingPairs.size()} intersections between ${containerClassCubes.size()} old and ${newBoxSet.size()} new boxes")

        List<ClassCube> newCubes = []
        numNewIntersections = intersectingPairs.size()

        if (intersectingPairs.size() == 0) {
            assert containerClassCubes.size() == 0 // First invocation
            for (cube in newBoxSet)
                addSafely(cube)
        } else {
            // 2. for each pair compute intersection
            Set<ClassCube> intersectedOldCubes = []
            Set<ClassCube> intersectedNewCubes = []
            for (pair in intersectingPairs) {
                def cubeFromContainer = (ClassCube) pair.first
                def newCube = (ClassCube) pair.second
                newCubes << cubeFromContainer.getIntersection(newCube)
                intersectedOldCubes << cubeFromContainer
                intersectedNewCubes << newCube
            }
            assert class2geomMap.keySet().equals(intersectedOldCubes)
            assert intersectedNewCubes.containsAll(newBoxSet)
            class2geomMap.clear()
            for (cube in newCubes)
                addSafely(cube)

        }

        numTotalIntersections += numNewIntersections
    }


    BoxSet getBoxSet() {
        return new BoxSet(class2geomMap.keySet().asList())
    }


    int size() {
        return 0  //TODO To change body of implemented methods use File | Settings | File Templates.
    }

    private def addSafely(ClassCube newClassCube) {
        def sameGeometryCube = class2geomMap[newClassCube]
        if (sameGeometryCube == null) {
            // all ok, there was no duplicate in the collection
            def geometricCubeKey = new GeometricCube(newClassCube)
            geom2classMap[geometricCubeKey] = newClassCube
        } else {
            log.warning("Trying to add a cube with already existing geometry to container (existing = ${sameGeometryCube.classCube}, new = $newClassCube")
            // retain old cube but merge their classes
            sameGeometryCube.classCube.classData.mergeClassData newClassCube.classData
        }
    }

    static class GeometricCube {
        ClassCube classCube

        GeometricCube(ClassCube aClassCube) {
            this.classCube = aClassCube
        }

        @Override
        boolean equals(Object other) {
            if (classCube != null) return classCube.geometryEquals(other)
            return false    // return false if cube == null
        }

        @Override
        int hashCode() {
            if (classCube != null) return classCube.geometryHashCode()
            return 0 // for null
        }
    }

}
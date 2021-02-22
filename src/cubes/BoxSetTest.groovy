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

import trees.TreeOfBoxesNode

class BoxSetTest extends GroovyTestCase {
    void testSimpleAddCubeCollection() {
        def a1 = new ClassCube(2)
        a1.with {
            setClassValue(1.0)
            setLower(1, 1.0)
        }
        def a2 = new ClassCube(2)
        a2.with {
            setClassValue(2.0)
            setUpper(1, 1.0)
        }
        def b1 = new ClassCube(2)
        b1.with {
            setClassValue(2.0)
            setUpper(0, 2.0)
        }
        def b2 = new ClassCube(2)
        b2.with {
            setClassValue(1.0)
            setLower(0, 2.0)
        }

        def cc = new BoxSet()
        cc.mergeBoxSetsViaIntersections(new BoxSet([a1, a2]))
        cc.mergeBoxSetsViaIntersections(new BoxSet([b1, b2]))
        assertEquals(4, cc.size())
    }

    void testPaperCollection() {
        def cc = new BoxSet()
        cc.mergeBoxSetsViaIntersections(ExampleCubesFromPaper.getA())
        cc.mergeBoxSetsViaIntersections(ExampleCubesFromPaper.getB())
        def cubes = cc
        assertEquals(10, cubes.size())
    }
    
    void testTreeNode() {
        def cc = new BoxSet()
        cc.mergeBoxSetsViaIntersections(ExampleCubesFromPaper.getA())
        cc.mergeBoxSetsViaIntersections(ExampleCubesFromPaper.getB())
        def Collection<ClassCube> cubes = cc
        
        // now the part for tree building
        def treeNode = new TreeOfBoxesNode(cubes)
        treeNode.buildTree()
        
        println "Tree before reduction of box set:"
        println treeNode

        def joiner = new JoinAdjacentCubes(cubes)
        def joinedCubes = joiner.joinAdjacentCubes()
        // now the part for tree building
        def reducedTreeNode = new TreeOfBoxesNode(joinedCubes)
        reducedTreeNode.buildTree()

        println "Tree after reduction (box merging):"
        println reducedTreeNode

    }
}

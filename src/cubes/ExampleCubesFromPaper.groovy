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

class ExampleCubesFromPaper {
    static Collection<ClassCube> getA() {
        def a1 = new ClassCube(2)
        a1.with {
            setClassValue(2)
            setUpper(0, 0.5)
            setLower(1, 0.375)
        }
        def a2 = new ClassCube(2)
        a2.with {
            setClassValue(1)
            setBounds(0, 0.5, 0.7)
            setLower(1, 0.55)
        }
        def a3 = new ClassCube(2)
        a3.with {
            setClassValue(2)
            setLower(0, 0.7)
            setLower(1, 0.55)
        }
        def a4 = new ClassCube(2)
        a4.with {
            setClassValue(1)
            setLower(0, 0.5)
            setBounds(1, 0.375, 0.55)
        }
        def a5 = new ClassCube(2)
        a5.with {
            setClassValue(1)
            setUpper(1, 0.375)
        }
        def colA = new BoxSet([a1, a2, a3, a4, a5])
        addClassProbVector(colA)
        return colA
    }

    static addClassProbVector(Collection<ClassCube> cubes) {
        double[] classProbVector = [0.3, 0.2, 0.5 ] as double []

        for (cube in cubes) {
            cube.classData.classProbDistribution = classProbVector
        }
    }
    
    static Collection<ClassCube> getB() {
        def b1 = new ClassCube(2)
        b1.with {
            setClassValue(2)
            setUpper(0, 0.5)
            setLower(1, 0.25)
        }
        def b2 = new ClassCube(2)
        b2.with {
            setClassValue(1)
            setBounds(0, 0.5, 0.75)
            setLower(1, 0.6)
        }
        def b3 = new ClassCube(2)
        b3.with {
            setClassValue(2)
            setLower(0, 0.75)
            setLower(1, 0.6)
        }
        def b4 = new ClassCube(2)
        b4.with {
            setClassValue(1)
            setLower(0, 0.5)
            setBounds(1, 0.25, 0.6)
        }
        def b5 = new ClassCube(2)
        b5.with {
            setClassValue(1)
            setUpper(1, 0.25)
        }
        def colB = new BoxSet([b1, b2, b3, b4, b5])

        addClassProbVector(colB)
        return colB

    }
}

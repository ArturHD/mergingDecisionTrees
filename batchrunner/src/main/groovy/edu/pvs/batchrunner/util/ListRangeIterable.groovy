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

package edu.pvs.batchrunner.util

/**
 * User: artur
 * Date: 1.09.2010
 * Time: 17:17:02
 * Generalizes Lists, Groovy's IntRanges, and "double ranges with step"
 * Ranges *include* the upper bound ("to")
 */
@Typed
class ListRangeIterable<T> implements Iterable<T> {
  Iterable myIterable

  def fromList(List newList) {
    myIterable = (List) newList
  }

  def fromConstant(Object constant) {
    List newList = [constant]
    myIterable = newList
  }

  def fromRange(Number start, Number upto, Number stepValue = 1) {
    if (stepValue == 1) {
      // use standard range
      myIterable = start..upto
    } else if (stepValue != 0) {
      // generate double values and put them into a list
      int size = Math.floor((upto - start) / stepValue) + 1
      List valList = new ArrayList<Number>((int)size)
      double current = start
      // use "upto" because it takes the highest (upto) value as well
      // org.codehaus.groovy.runtime.DefaultGroovyMethods.upto(start, upto, {valList << it})
      for (int i : 0..<size) {
        valList.add(new Double(current))
        current += stepValue
      }
      myIterable = valList

    } else {
      throw new IllegalArgumentException("Wrong range specification, step is 0 (start = $start, upto = $upto")
    }
  }

  // The returns the iterator of our iterable data structure
  // The only interface function we need to implement

  Iterator<T> iterator() {
    return myIterable.iterator()
  }
}

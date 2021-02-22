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

import groovy.transform.TupleConstructor

@Typed @TupleConstructor
class Event implements Comparable<Event> {
    @Override
    int compareTo(Event o) {
        int valueComparisonResult =  Double.compare(value, o.value)
        if (valueComparisonResult != 0)
            return valueComparisonResult
        else
            return type.compareTo(o.type) // Implementation detail from (Bin)C45Split: Intervals are (lowwer,upper], see EventType
    }

    enum EventType {
        A_END, B_END, A_START, B_START  // Implementation detail from (Bin)C45Split: Intervals are (lowwer,upper], thus intervals end before others start
    }
    double value
    EventType type
    Cube cube

    @Override
    String toString() {
        return "[$value: $type ($cube)]"
    }
}

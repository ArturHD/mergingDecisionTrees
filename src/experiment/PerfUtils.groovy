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

package experiment

import edu.pvs.batchrunner.ExperimentResult
import edu.pvs.batchrunner.util.TimeDate

@Typed @Trait
class PerfUtils {

    /** Returns a pair consisting of current time in millis and current memory used */
    Pair<Long, Long> tic() {
        new Pair<Long, Long>(System.currentTimeMillis(), Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
    }

    String toDiffString(Pair<Long, Long> t0, Pair<Long, Long> t1) {
        def t = t1.first - t0.first
        def m = Math.round((t1.second - t0.second) / 1024)
        def result = new StringBuilder()
        if (t >= 1000)
            result.append(Math.round(t / 1000)).append(" s")
        else
            result.append(t).append(" ms")
        result.append(" and ")
        if (m >= 1024)
            result.append(Math.round(m / 1024)).append(" MiB")
        else
            result.append(m).append(" kiB")
        return result.toString()
    }

    Pair<Long, Long> plistToP(List<Pair<Pair<Long, Long>, Pair<Long, Long>>> pairList) {
        long t = 0
        long m = 0
        for (pairs in pairList) {
            t += pairs.second.first - pairs.first.first
            m = Math.max(m, pairs.second.second - pairs.first.second)
        }
        return new Pair<Long, Long>(t, m)
    }

    /**
     * Returns the time elapsed between the two points in time
     * @param t0
     * @param t1
     * @return the time elapsed between the two points in time
     */
    long timeDiff(Pair<Long, Long> t0, Pair<Long, Long> t1) {
        t1.first - t0.first
    }

    /**
     * Returns the diff. in mem consumption between the two points in time
     * @param t0
     * @param t1
     * @return the diff. in mem consumption between the two points in time
     */
    long memDiff(Pair<Long, Long> t0, Pair<Long, Long> t1) {
        t1.second - t0.second
    }

    void appendPerfLogEntry(ExperimentResult r, String operation, long inSize1, long inSize2, long outSize1, long outSize2, long timeDelta, long memDelta) {

        def pConf = (String) r.Pconf
        def classif = (pConf == "c" || pConf == "d") ? "tmm" : "tmo"

        // build entry
        String line = "$operation,${r.dataset},$classif,$inSize1,$inSize2,$outSize1,$outSize2,$timeDelta,$memDelta\n"

        // write entry
        PerfLogger.perfLog.write(line)
        PerfLogger.perfLog.flush()
    }
}

class PerfLogger {

    final static FileWriter perfLog = new FileWriter("results/performance-${TimeDate.getCurrentDateTime(null)}.csv")
    static {
        perfLog.write("operation,dataset,classifier,inSize1,inSize2,outSize1,outSize2,timeDelta,memDelta\n")
    }
}
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

import weka.core.Utils
import weka.core.converters.ConverterUtils.DataSource

/**
 * Simple Groovy script to extract the attribute stats from a dataset.
 * Supports the following parameters:
 * -t dataset-filename
 *
 * @author FracPete (fracpete at waikato dot ac dot nz)
 */

// get parameters
// 1. data
tmp = Utils.getOption('t', args)
if (tmp == '') throw new Exception('No dataset provided!')
dataset = DataSource.read(tmp)

// print stats
for (i = 0; i < dataset.numAttributes(); i++) {
  att   = dataset.attribute(i)
  stats = dataset.attributeStats(i)
  println "\n" + (i+1) + ". " + att.name()
  if (att.isNominal()) {
    println "Type: nominal"
    println "distinct: " + stats.distinctCount
    println "int: " + stats.intCount
    println "real: " + stats.realCount
    println "total: " + stats.totalCount
    println "unique: " + stats.uniqueCount
    println "label stats:"
    for (n = 0; n < stats.nominalCounts.length; n++) {
      println " - " + att.value(n) + ": " + stats.nominalCounts[n]
    }
  }
  else if (att.isNumeric()) {
    println "Type: numeric"
    println "distinct: " + stats.distinctCount
    println "int: " + stats.intCount
    println "real: " + stats.realCount
    println "total: " + stats.totalCount
    println "unique: " + stats.uniqueCount
    println "numeric stats:"
    println " - count: " + stats.numericStats.count
    println " - max: " + stats.numericStats.max
    println " - min: " + stats.numericStats.min
    println " - mean: " + stats.numericStats.mean
    println " - stdDev: " + stats.numericStats.stdDev
    println " - sum: " + stats.numericStats.sum
    println " - squmSq: " + stats.numericStats.sumSq
  }
  else {
    println "Unhandled attribute type: " + att.type()
  }
}

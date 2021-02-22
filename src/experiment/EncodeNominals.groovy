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

/**
 * Script to translate nominal values of the result csv files to numeric ones for better tool support.
 *
 * User: flangner
 * Date: 23.07.12
 * Time: 11:25
 */

def Map<String, Integer> conversionMap =
    ["a": 0,"b": 1,"c": 2,"d": 3,
     "Distance": 0, "Greedy": 1,
     "J48": 0, "RandomForest": 1,
     "Linear": 0, "DAC": 1,
     "MIN_NUM_SPLITS": 0, "INFO_GAIN": 1,
     "TRAINING_DATA": 0, "SEPARATE_PRUNING_DATA": 1,
     "j48": 0, "rf": 1, "mtc": 2, "cd": 3, "vc": 4, "tc": 5, "fcc": 6, "opc": 7, "rsc": 8,
     "false": 0, "true": 1,
     "LetterRecognition.arff": 0, "synthetic.arff": 1, "cd_synthetic.arff": 2, "iris.arff": 3,
     "segment-challenge.arff" : 4, "Cardiotocography.arff": 5, "OzoneLeveLDetection.arff": 6, "PageBlocks.arff": 7,
     "WallFollowingRobotNavigation.arff": 8, "spambase.arff": 9, "MAGICGammaTelescope.arff": 10,
     "MiniBooNE_balanced.arff": 11, "yeast.arff": 12, "2DdataCircleGSev1Sp1Train.arff": 13, "2DTest.arff": 14,
     "NaN": -1]

// get csv file name from command line
def fileName
if (args.size() <= 0) {
    throw new Exception("No CSV file given!")
} else {
    fileName = args[0]
}

def File source = new File(fileName)
def File target = new File(fileName + ".numeric.csv")

target.withWriter { out ->
    source.eachLine { line ->

        def newLine = ""
        line.split(",").each { word ->

            if (conversionMap.containsKey(word)) {

                newLine += conversionMap.get(word)
            } else {

                newLine += word
            }

            newLine += ','
        }

        // remove the unnecessary trailing comma
        newLine = newLine.substring(0, newLine.length() - 1)

        out.println(newLine)
    }
}
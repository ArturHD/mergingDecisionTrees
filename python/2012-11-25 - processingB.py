#  Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala
#
#  Permission is hereby granted, free of charge, to any person obtaining a copy
#  of this software and associated documentation files (the "Software"), to deal
#  in the Software without restriction, including without limitation the rights
#  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
#  copies of the Software, and to permit persons to whom the Software is
#  furnished to do so, subject to the following conditions:
#
#  The above copyright notice and this permission notice shall be included in
#  all copies or substantial portions of the Software.
#
#  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
#  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
#  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
#  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
#  THE SOFTWARE.
#
#

from pandas import *
from openpyxl import *
from xlrd import *
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.pyplot as plt

xls = ExcelFile('performance-all-24.11@13.00.xlsx')
df = xls.parse('data')

# get dataset names as an array
dataNames = numpy.unique( df['dataset'].values)
# get operation names as an array
opsNames = numpy.unique( sb_tmo['operation'].values)

perOperationDict = dict()
for operationName in opsNames:
	perDatasetDict = dict()
	# Subselect into new object: only rows with datasetName and tmm 
	# datasetDF = df[(df.dataset == datasetName) & (df.classifier == 'tmm')]
	#
	for datasetName in dataNames:
		# Subselect operation in column
		preSeriesDF = df[(df.dataset == datasetName) & (df.classifier == 'tmm') & (df.operation == operationName)]
		# create a Series only with column 'timeDelta' and index = 'inTotal'.values
		opSeries = Series((preSeriesDF['timeDelta']).values , index = (preSeriesDF['inTotal']).values)
		# sort by inTotal column  
		opSeries = opSeries.sort_index()
		# Optionally - apply rolling mean (SMA)
		opSeries = rolling_mean(opSeries, 10)
		# add to dictionary
		perDatasetDict[datasetName] = opSeries
	perOperationDict[operationName] = perDatasetDict


# plot over all datasets per diagram
linestyles = ['-', '--', ':','-.', '-', '--']
colors = ('b', 'g', 'r', 'c', 'm', 'y', 'k')
dataNamesDict = {'Cardiotocography.arff': 'D1', 'WallFollowingRobotNavigation.arff': 'D2', 'spambase.arff': 'D3', 'MAGICGammaTelescope.arff': 'D4'}
matplotlib.rcParams.update({'font.size': 24})
matplotlib.rcParams.update({'font.family' : 'times new roman'})


def plotForOp(perOperationDict, opName, logYaxis, goodDatas, xlabelName = '', xlimitval = 100000):

	operDict = perOperationDict[opName]
	plt.close()
	fig = plt.figure()
	counter = 0
	for dataset in goodDatas:
		color = colors[counter % len(colors)]
		series = operDict[dataset]
		series.plot(label = dataNamesDict[dataset], linestyle=linestyles[counter], color=color, linewidth=3.0, logy=logYaxis )
		counter = counter+1
	plt.legend(loc='best')
	#plt.ylabel('time (ms)')
	plt.title(xlabelName)
	plt.xlim([0, xlimitval])
	
	# save to file <opName>.pdf
	pp = PdfPages('perf-'+opName +'.pdf')
	pp.savefig(fig)
	pp.close()


goodDatas = ['Cardiotocography.arff', 'WallFollowingRobotNavigation.arff', 'spambase.arff', 'MAGICGammaTelescope.arff']
xlimits = {'ComputeCoverage': 100000, 'ConflictResolution': 130000, 'JoinOfAdjacentCubes': 100000, 'PruneBox': 30000, 'TotalTime': 2000, 'TreeBuild': 1000, 'Unify': 2000}
xlabelNames = {'ComputeCoverage': 'Pruning', 'ConflictResolution': 'Conflict Resolution', 'JoinOfAdjacentCubes': 'JoinOfAdjacentCubes', 'PruneBox': 'Prune Sorting', 'TotalTime': 'Total Time', 'TreeBuild': 'Tree Growing', 'Unify': 'Unification'}


for opName in opsNames:
	logYaxis = False
	plotForOp(perOperationDict, opName, logYaxis, goodDatas, xlabelNames[opName], xlimits[opName])

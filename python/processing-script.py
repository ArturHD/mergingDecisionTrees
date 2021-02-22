#-------------
#Preliminaries:
#ipython + tools (plotting...) installed
#easy_install pandas
#easy_install xlrd
#easy_install openpyxl

#start with (from the project root):
#cd results\performance\python
#ipython -pylab
#--------------


from pandas import *
from openpyxl import *
from xlrd import *

xls = ExcelFile('performance-all-24.11@13.00.xlsx')
df = xls.parse('data')


# Subselect into new object: only rows with spambase and tmo 
sb_tmo = df[(df.dataset == 'spambase.arff') & (df.classifier == 'tmm')]

# Subselect operation 'ComputeCoverage'
sb_tmo_coverage = sb_tmo[sb_tmo.operation == 'ComputeCoverage']

# Obsolete: create a new DataFrame only with columns 'inTotal' and 'timeDelta' and index = 'inTotal'.values
# sb_tmoCov = DataFrame(sb_tmo_coverage[['inTotal', 'timeDelta']] , index = (sb_tmo_coverage['inTotal']).values)

# create a Series only with column 'timeDelta' and index = 'inTotal'.values
coverage = Series((sb_tmo_coverage['timeDelta']).values , index = (sb_tmo_coverage['inTotal']).values, name = 'Pruning')


# sort by inTotal column (see http://pandas.pydata.org/pandas-docs/stable/basics.html#sorting-by-index-and-value)
# sb_tmoCov = sb_tmoCov.sort_index(by='inTotal')
coverage = coverage.sort_index()

# apply rolling mean (SMA)
covMean = rolling_mean(coverage, 5)

# plotting
import matplotlib.pyplot as plt

plt.figure()
# sb_tmoCov.plot(x='inTotal', y='timeDelta')
# covMean.plot(logy=True)
covMean.plot(logy=True)
plt.legend(loc='best')
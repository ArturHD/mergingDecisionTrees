%  Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala
%
%  Permission is hereby granted, free of charge, to any person obtaining a copy
%  of this software and associated documentation files (the "Software"), to deal
%  in the Software without restriction, including without limitation the rights
%  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
%  copies of the Software, and to permit persons to whom the Software is
%  furnished to do so, subject to the following conditions:
%
%  The above copyright notice and this permission notice shall be included in
%  all copies or substantial portions of the Software.

%  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
%  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
%  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
%  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
%  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
%  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
%  THE SOFTWARE.

classdef pivotTools < handle
% Provides tools for reading in and analysing / pivoting a csv file
% See the static function pivotTools.run() for most basic usage
% 
% ======== Parameters =========
properties
    cleanFromNanAndInf = true;
end

properties
    data;           % last data
    rowsWithNaNs;   % list of removed rows with NaNs
end

methods
      function setData(s, newData)
        s.data = newData;
      end

      function data = readInCSV(s, fullpath, startingLineFromZero)
        % Reads a csv file
        % WARNING: ONLY PURELY NUMERICAL DATA (except for header lines)
        data = csvread(fullpath, startingLineFromZero, 0);
        if s.cleanFromNanAndInf
            % clean up from NaNs
            [data, s.rowsWithNaNs] = s.cleanNanAndInf(data);
        end
        s.data = data;
    end

    function data = readInMixedCSV(s, fullpath, startingLineFromZero, varargin)
        % reads a csv file 
        % Can reads-in mixed string / numerical files by using txt2mat options
        % INPUT: ...varargin - arguments passed directly to txt2mat
        data = txt2mat(fullpath, startingLineFromZero, varargin{:});
        s.data = data;
    end
    

    function [data, rowsWithNaNs] = cleanNanAndInf(s, data)
        % removes NaNs and Infs from data. Returns the cleaned data and
        % the list of rows with NaN's in the original matrix
        rowsWithNaNs = any(isnan(data),2);
        data(rowsWithNaNs,:) = [];
        rowWithInfs = any(isinf(data),2);
        data(rowWithInfs,:) = [];        
    end

    function addColumns(s, newColumns)
        % adds new columns (at the end) to data
        s.checkNewColumnsForSize(newColumns);
        s.data = [s.data newColumns];
    end
    
    function replaceColumns(s, newColumns, columnIndices)
        % replaces columns at given indices
        s.checkNewColumnsForSize(newColumns);
        s.data(:, columnIndices) = newColumns;
    end
       
    function columns = getColumns(s, columnIndices)
        columns = s.data(:, columnIndices);
    end
    
    
    function checkNewColumnsForSize(s, newColumns)
        nRowsData = size(s.data,1);
        nRowsCol = size(newColumns,1);
        if (nRowsData ~= nRowsCol)
            error('pivotTools:addColumn', 'Wrong size of the column: should be %d, is %d', nRowsData, nRowsCol);
        end
    end
            
    
    function newdata = filterOneColumn(s, colIndex, keptValues)
        % filters indata by the keptValues of one column
        % INPUT: colIndex - index of the column (value or CA{1}) by which to filt
        %       keptValues - values in the resp. column to for which the rows should be kept

        indata = s.data;
        if iscell(colIndex), colIndex = colIndex{1}; end;
        targetCol = indata(:, colIndex);
        keepRows = [];
        numVals = length(keptValues);
        for i = 1:numVals
            keepRows = union(keepRows, find(targetCol == keptValues(i)));
        end
        newdata = indata(keepRows, :);
        s.data = newdata;
    end
    
    function [combinations, dataByCombination] = groupByCombinations(s, pivotColumns)
        %Partitions Input according to different combination of the values in the columns specified by
        %pivotColumns
        % Input: a matrix with input data; if empty, use s.data
        % pivotColumns: column indices (1-based) of columns for which value combinations we seek
        % Output:
        %   combinations: a k-by-d matrix, each row is another combination, and d=lenght(pivotColumns)
        %   dataByCombination: a k-by-1 CA,  k-th cell holds a matrix with all rows corresponding to
        %   combinations(k)

        %if isempty(Input), Input = s.data; end
        Input = s.data;
        nRows = size(Input,1);

        %sortiert Input nach den Pivotelementen
        SortedInput= sortrows(Input,pivotColumns);
        %Matrix in der nur die Spalten mit den Pivotelementen stehen
        PivotsOnly = SortedInput(:,pivotColumns);

        %UniqueInput Matrix,in der jede Pivotkombination einmaldrin steht
        %firstRowNewCombinationsInData weist einer Zeile in UniqueInput die erste Zeile in
        %PivotsOnly zu in der diese Zeile auftritt
        [combinations, firstRowNewCombinationsInData] = unique(PivotsOnly, 'rows','first');

        numCombinations = size(combinations,1);


        %In die erste Zeile kommt die Matrix mit den Pivotelementwerte
        %In der zweiten Zeile stehen die dazugeh�rigen sortierten Matrizen
        dataByCombination = cell(numCombinations, 1);


        for k = 1:numCombinations-1
            startIndex = firstRowNewCombinationsInData(k,1);   
            endIndex = firstRowNewCombinationsInData(k+1,1)-1; 
            dataByCombination{k} = SortedInput(startIndex:endIndex, :);
        end
        startIndex = firstRowNewCombinationsInData(numCombinations,1);   
        endIndex = nRows; 
        dataByCombination{numCombinations} = SortedInput(startIndex:endIndex, :);    
    end

    
    function aggResult = aggregate(s, dataByCombination, funHandle, columnFilter)
        % aggregates results by applying a funHandle to each matrix dataByCombination{k}
        % optionally, it filters the input data, leaving only columns in columnFilter
        % Inputs
        %   dataByCombination: a k-by-1 CA,  k-th cell holds a matrix with all rows for a spec. combination
        %   funHandle: handle to a function to be applied to each matrix (functions like sum, mean, which
        %               compute a value on each column)
        %   columnFilter: optional list of column indices which should survive
        % OUTPUTS:
        %   aggResult: a k-by-d matrix  k-th row holds a vector with aggregated results for the
        %   kth combination; each vector is possibly filtered by columnFilter

        numColumnsPerData = size(dataByCombination{1}, 2);
        if nargin < 4
            columnFilter = 1:numColumnsPerData;
        end
        numCombinations = size(dataByCombination,1);
        aggResult = zeros(numCombinations,length(columnFilter));
        
        for i = 1:numCombinations
            data = dataByCombination{i}(:,columnFilter);
            if s.cleanFromNanAndInf
                % clean up from NaNs
                [data, s.rowsWithNaNs] = s.cleanNanAndInf(data);
            end
            % apply function only we have >1 rows, otherwise different columns are aggregated
            if size(data,1) > 1
                result = funHandle(data);
            else
                result = data;
            end
            if ~isempty(result)
                aggResult(i,:) = result;
            end
        end
    end
    
    function columnsFromAllComb = getColumnPerCombination(s, dataByCombination, targetColumn)
        % For each pre-computed combination gets a targetColumn, and concatenates these
        % columns into a matrix.
        % We (at first) assume that each combination has same # of rows
        
        % numColumnsPerData = size(dataByCombination{1}, 2);
        numRowsPerData = size(dataByCombination{1}, 1);
        numCombinations = size(dataByCombination,1);
        columnsFromAllComb = zeros(numRowsPerData, numCombinations);
        for i = 1:numCombinations
            data = dataByCombination{i}(:,targetColumn);
            if s.cleanFromNanAndInf
                % clean up from NaNs
                data = s.cleanNanAndInf(data);
            end
            columnsFromAllComb(:, i) = data;
        end
    end
    
    
end
methods (Static = true)
    function run()
        % Create test data, class and set data
        testData =[1 2 4 3; 1 2 3 6; 7 8 1 8; 9 10 3 -1; 7 8 1 9];
        columnsWithValueCombinations = [1 2];
        outputColumns = [3 4];
        
        s = pivotTools();
        s.setData(testData);
        
        %% Finds all unique value combinations for specified columns
        [combinations, dataByCombination] = s.groupByCombinations(columnsWithValueCombinations);
        disp('Unique value combinations for input columns:');        
        disp(combinations);
        
        %% Aggregates all lines with a unique value combination using the function specified as funHandle
        funHandle = @mean;
        aggResult = s.aggregate(dataByCombination, funHandle);
        disp('Aggregation result for output columns:');
        disp(aggResult(:,outputColumns));
    end
end

end
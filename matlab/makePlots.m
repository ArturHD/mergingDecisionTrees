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

classdef makePlots < handle

    properties (GetAccess = protected)
        
        % object with groupped / pivoted data
        pt = pivotTools();
    end
    
    properties (Constant = true)
        
        workingDir = 'results/';
        raw = {'data0.csv.numeric.csv', 'data1.csv.numeric.csv', 'data2.csv.numeric.csv', 'data3.csv.numeric.csv', ...
               'data4.csv.numeric.csv', 'data5.csv.numeric.csv'};
        figFontSize = 20;
        defaultColormap = copper;
        
        savePdfPath = [makePlots.workingDir  'fig/'];
        saveSrcPath = [makePlots.workingDir  'figsources/'];
        
        columns = {'dataset', 'Ek', 'treeType', 'Pdisc', 'NumFolds', 'Ncentro', 'MS', 'SumStra', 'FinalyClassif', ...
                   'Pconf', 'Pprun', 'PprunMaxBoxes', 'Pmerge', 'Pgrow', 'PprunDS', 'Etf', 'Esim', ...
                   'EuseSameRandomSampls', 'Eparallel', 'pctCorrect', 'pctIncorrect', 'pctUnclassified', ...
                   'precisionClass0', 'recallClass0', 'specificityClass0', 'accuracyClass0', 'ROCAUCClass0', ...
                   'classifierBuildTimeMax', 'classifierBuildTimeAvg', 'classifierBuildPeakMemUsageMax', ...
                   'classifierBuildPeakMemUsageAvg', 'classifierClassificationTimeMax', 'classifierClassificationTimeAvg', ...
                   'classifierClassificationPeakMemUsageMax', 'classifierClassificationPeakMemUsageAvg', ...
                   'numClasses', 'numAttributes', 'numInstances', 'conflictBoxes', 'mergedBoxesCount', ...
                   'cubesBuiltMax', 'cubesBuiltAvg', 'cubeCountRatioAfterMergingMax', 'cubeCountRatioAfterMergingAvg', ...
                   'cubeCompressionRatioMax', 'cubeCompressionRatioAvg', 'finalCubeCountMax', 'finalCubeCountAvg', ...
                   'cutBoxesAtBuildTreeMax', 'cutBoxesAtBuildTreeAvg', 'treeDepthMax', 'treeDepthAvg'};
        
        ttFilters = {{'treeType', 0}, {'treeType', 7}, {'treeType', 4}, {'treeType', 2}};
        ttNames = {'J48', 'SBa', 'SBb', 'VC', 'TMo', 'TMm'};
        
        xLabels = {'Number k of fragments', 'Ek-compression factor', 'max_{box}', 'Dataset', ...
            'D1               D2                 D3                D4                D5     ', ...
            'D1               D2                D3                D4                 D5       '};
        yLabels = {'Perc. correctly classified', 'Average build time (ms)'};
        titles = {'k = '};
               
        dsFilters = {{'dataset', 5}, {'dataset', 8}, {'dataset', 9}, {'dataset', 10}, {'dataset', 0}, {'dataset', 11}};
        dsNames = {'D1', 'D2', 'D3', 'D4', 'D5', 'D6'};
        % dsNames = {'Cardiotocography', 'WallFollowingRobotNavigation', 'spambase', 'MAGICGammaTelescope', ...
        %           'LetterRecognition', 'MiniBooNE'};
    end
    
    methods
                
        function data = load(s, fileName)
            
            firstLineToReadZeroBased = 1;
            data = s.pt.readInCSV([makePlots.workingDir fileName], firstLineToReadZeroBased);
        end
        
        function dataByCombination = group(s, data, columnNames)
            
            % group the data
            s.pt.setData(data);
            colIndices = s.name2col(columnNames);
            [~, dataByCombination] = s.pt.groupByCombinations(colIndices);
        end
        
        function filteredData = filter(s, data, rowFilters)
            
            % rowFilters format: {{'columnName', value}, ...}
            
            filteredData = data;
            % filter rows
            for filter = rowFilters 
                colIndices = s.name2col(filter{1}{1});
                idx = filteredData(:,colIndices) == filter{1}{2};
                filteredData = filteredData(idx,:);
            end
        end
        
        function filteredData = negatedFilter(s, data, rowFilters)
            
            % rowFilters format: {{'columnName', value}, ...}
            
            filteredData = data;
            % filter rows
            for filter = rowFilters 
                colIndices = s.name2col(filter{1}{1});
                idx = filteredData(:,colIndices) ~= filter{1}{2};
                filteredData = filteredData(idx,:);
            end
        end
        
        function selectedData = select(s, data, columnName)
            
            % now aggregate and filter columns
            s.pt.setData(data);
            funHandle = @sum;
            selectedData = s.pt.aggregate(data, funHandle, s.name2col(columnName));
        end
       
        function colIndices = name2col(s, columnNames)
            % find the column indices corresponding to one or more column names

            if ~iscell(columnNames), columnNames = {columnNames}; end
            numNames = length(columnNames);
            colIndices = zeros(numNames, 1);

            for nameIdx = 1:numNames
                actName = columnNames{nameIdx};
                index = find(ismember(s.columns, actName) == 1);
                colIndices(nameIdx) = index;
            end
        end
        
        function store(s, fileName)
            
            % store the current plot to file
            saveas(gcf, [s.savePdfPath fileName '.pdf'], 'pdf');
            saveas(gcf, [s.saveSrcPath fileName '.fig'], 'fig'); 
            close all;
        end
        
        function fig1(s) 
            
            % onepass overview
            
            rawData = s.load(makePlots.raw{2});
            
            % get data for the baseline
            baselines = s.filter(s.load(makePlots.raw{1}), [{{'Pdisc', 0}} makePlots.ttFilters(1)]);           
            baseline = [s.select({s.filter(baselines, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';

            % one plot for each ek
            index = 1;
            for ek = [1,4,16,64]
                data = s.filter(rawData, {{'Ek', ek}});
                
                % exclude miniboone
                %data = s.negatedFilter(data, {{'dataset', 11}});
                
                dataSBa = s.filter(data, [makePlots.ttFilters(2) {{'Ncentro', 0.5}} {{'SumStra', 1}}]);
                dataSBa = [s.select({s.filter(dataSBa, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBa, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBa, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBa, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBa, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBa, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataSBb = s.filter(data, [makePlots.ttFilters(2) {{'Ncentro', 0.5}} {{'SumStra', 0}}]);
                dataSBb = [s.select({s.filter(dataSBb, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBb, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBb, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBb, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBb, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataSBb, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataVC = s.filter(data, makePlots.ttFilters(3));
                dataVC = [s.select({s.filter(dataVC, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataMTO = s.filter(data, [makePlots.ttFilters(4) {{'Pconf', 1}} {{'Pprun', 200}}]);
                dataMTO = [s.select({s.filter(dataMTO, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataMTI = s.filter(data, [makePlots.ttFilters(4) {{'Pconf', 2}} {{'Pprun', 30}}]);
                dataMTI = [s.select({s.filter(dataMTI, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                subplot(1,4,index);
                index = index + 1;
                    
                makePlots.barPlot([dataSBa, dataSBb, dataMTO, dataMTI, dataVC], [], [], [], [0 7 25 100], ...
                        {makePlots.dsNames{1}, makePlots.dsNames{2}, makePlots.dsNames{3}, makePlots.dsNames{4}, ...
                        makePlots.dsNames{5}, ...
                        makePlots.dsNames{6} ...
                        }, baseline, [makePlots.titles{1} int2str(ek)]);
            end
            
            llabels = {makePlots.ttNames{2},makePlots.ttNames{3},  makePlots.ttNames{5}, makePlots.ttNames{6}, ...
                       makePlots.ttNames{4}, makePlots.ttNames{1}};
                   
            legend(llabels, 'Position',[.1,.2,.2,.1],'FontSize',10);
            
            s.store('1.onePass');
        end

        function fig14(s) 
            
            % one plot for each ek
            index = 1;
            llabels = {makePlots.ttNames{5}, makePlots.ttNames{6}, makePlots.ttNames{4}, makePlots.ttNames{1}};
            for ek = [1,4,16,64]
                
                % onepass overview
            
                rawData = s.load(makePlots.raw{2});
            
                % get data for the baseline
                baselines = s.filter(s.load(makePlots.raw{1}), [{{'Pdisc', 0}} makePlots.ttFilters(1)]);           
                baseline = [s.select({s.filter(baselines, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(baselines, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                data = s.filter(rawData, {{'Ek', ek}});
                
                % exclude miniboone
                %data = s.negatedFilter(data, {{'dataset', 11}});
                
                dataVC = s.filter(data, makePlots.ttFilters(3));
                dataVC = [s.select({s.filter(dataVC, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataVC, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataMTO = s.filter(data, [makePlots.ttFilters(4) {{'Pconf', 1}} {{'Pprun', 200}}]);
                dataMTO = [s.select({s.filter(dataMTO, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTO, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                dataMTI = s.filter(data, [makePlots.ttFilters(4) {{'Pconf', 2}} {{'Pprun', 30}}]);
                dataMTI = [s.select({s.filter(dataMTI, makePlots.dsFilters(1))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(2))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(3))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(4))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(5))}, 'pctCorrect'), ...
                        s.select({s.filter(dataMTI, makePlots.dsFilters(6))}, 'pctCorrect') ...
                        ]';
                
                makePlots.barPlot([dataMTO, dataMTI, dataVC], [], [], [], [0 7 25 100], ...
                        {makePlots.dsNames{1}, makePlots.dsNames{2}, makePlots.dsNames{3}, makePlots.dsNames{4}, ...
                        makePlots.dsNames{5}, makePlots.dsNames{6}}, baseline, [makePlots.titles{1} int2str(ek)]);
                    
                
                if (index == 1)
                    legend(llabels, 'Position',[.3,.2,.3,.4],'FontSize',20);
                end
                
                str = ['1-' int2str(index) '-onePass'];
                
                index = index + 1;
                
                s.store(str);
            end
            
        end

        
        function fig2a(s)
            
            % CBC accuracy
            
            rawData = s.load(makePlots.raw{3});
            data = s.filter(rawData, {{'Ek', 16}});
            
            % exclude miniboone
            data = s.negatedFilter(data, {{'dataset', 11}});
            
            % trim data
            data = s.negatedFilter(data, {{'Ncentro', 0.7}});
            data = s.negatedFilter(data, {{'Ncentro', 0.8}});
            
            
            dataVC = s.filter(data, makePlots.ttFilters(3));
            dataVC = s.group(dataVC, {'dataset'});
            dataVC = s.select(dataVC, 'pctCorrect');

            dataSBa = s.filter(data, [{{'SumStra', 1}} makePlots.ttFilters(2)]);
            dataSBa = s.group(dataSBa, {'dataset', 'Ncentro'});
            dataSBa = s.select(dataSBa, 'pctCorrect');

            dataSBb = s.filter(data, [{{'SumStra', 0}} makePlots.ttFilters(2)]);
            dataSBb = s.group(dataSBb, {'dataset', 'Ncentro'});
            dataSBb = s.select(dataSBb, 'pctCorrect');
                
            makePlots.barPlot([dataSBa dataSBb], makePlots.xLabels{5}, makePlots.yLabels{1}, ...
                               {makePlots.ttNames{2}, makePlots.ttNames{3}, makePlots.ttNames{4}}, [0 21 25 100], ...
                               {'0.01','0.1','0.3','0.5', ...
                               '0.01','0.1','0.3','0.5', ...
                               '0.01','0.1','0.3','0.5', ...
                               '0.01','0.1','0.3','0.5', ...
                               '0.01','0.1','0.3','0.5'}, ...
                                dataVC, []);
            s.store('2.SBx-acc');
        end
        
        function fig2b(s)
            
            % CBC accuracy
            rawData = s.load(makePlots.raw{3});
            
            % exclude miniboone
            data = s.negatedFilter(rawData, {{'dataset', 11}});
                        
            dataVC = s.filter(data, makePlots.ttFilters(3));
            dataVC = s.group(dataVC, {'dataset', 'Ek'});
            dataVC = s.select(dataVC, 'pctCorrect');

            dataSBa = s.filter(data, [{{'SumStra', 1}} makePlots.ttFilters(2)]);
            dataSBa001 = s.filter(dataSBa, {{'Ncentro', 0.01}});
            dataSBa001 = s.group(dataSBa001, {'dataset', 'Ek'});
            dataSBa001 = s.select(dataSBa001, 'pctCorrect');
            
            dataSBa01 = s.filter(dataSBa, {{'Ncentro', 0.1}});
            dataSBa01 = s.group(dataSBa01, {'dataset', 'Ek'});
            dataSBa01 = s.select(dataSBa01, 'pctCorrect');
            
            dataSBa03 = s.filter(dataSBa, {{'Ncentro', 0.3}});
            dataSBa03 = s.group(dataSBa03, {'dataset', 'Ek'});
            dataSBa03 = s.select(dataSBa03, 'pctCorrect');
            
            dataSBa05 = s.filter(dataSBa, {{'Ncentro', 0.5}});
            dataSBa05 = s.group(dataSBa05, {'dataset', 'Ek'});
            dataSBa05 = s.select(dataSBa05, 'pctCorrect');
                
            makePlots.barPlot([dataSBa001 dataSBa01 dataSBa03 dataSBa05], makePlots.xLabels{6}, ...
                               makePlots.yLabels{1}, {'0.01','0.1','0.3','0.5', makePlots.ttNames{4}}, [0 36 25 100], ...
                               {'2^{0}', '2^{2}', '2^{4}', '2^{6}', '2^{7}', '2^{8}', '2^{9}', ...
                               '2^{0}', '2^{2}', '2^{4}', '2^{6}', '2^{7}', '2^{8}', '2^{9}', ...
                               '2^{0}', '2^{2}', '2^{4}', '2^{6}', '2^{7}', '2^{8}', '2^{9}', ...
                               '2^{0}', '2^{2}', '2^{4}', '2^{6}', '2^{7}', '2^{8}', '2^{9}', ...
                               '2^{0}', '2^{2}', '2^{4}', '2^{6}', '2^{7}', '2^{8}', '2^{9}'}, ...
                                dataVC, []);
            s.store('2.SBa-acc');
        end
        
        function fig3(s)
            
            % CBC performance
            
            rawData = s.load(makePlots.raw{3});
                        
            % one plot for each dataset
            for i=1:6
                data = s.filter(rawData, makePlots.dsFilters(i));
                
                dataVC = s.filter(data, makePlots.ttFilters(3));
                dataVC = s.group(dataVC, {'Ek'});
                dataVC = s.select(dataVC, 'classifierBuildTimeAvg');
                
                dataSBa = s.filter(data, [{{'SumStra', 1}} makePlots.ttFilters(2)]);
                dataSBa = s.group(dataSBa, {'Ek', 'Ncentro'});
                dataSBa = s.select(dataSBa, 'classifierBuildTimeAvg');
                
                dataSBb = s.filter(data, [{{'SumStra', 0}} makePlots.ttFilters(2)]);
                dataSBb = s.group(dataSBb, {'Ek', 'Ncentro'});
                dataSBb = s.select(dataSBb, 'classifierBuildTimeAvg');
                
                makePlots.barPlot([dataSBa dataSBb], makePlots.xLabels{2}, makePlots.yLabels{2}, ...
                                  {makePlots.ttNames{2}, makePlots.ttNames{3}, makePlots.ttNames{4}}, [], ...
                                  {'1-0.01','1-0.1','1-0.3','1-0.5', '1-0.7','1-0.8', '4-0.01','4-0.1','4-0.3', ...
                                    '4-0.5','4-0.7','4-0.8', '16-0.01','16-0.1','16-0.3','16-0.5','16-0.7','16-0.8', ...
                                    '64-0.01','64-0.1','64-0.3','64-0.5','64-0.7','64-0.8', ...
                                    '128-0.01','128-0.1','128-0.3','128-0.5','128-0.7','128-0.8', ...
                                    '256-0.01','256-0.1','256-0.3','256-0.5','256-0.7','256-0.8', ...
                                    '512-0.01','512-0.1','512-0.3','512-0.5','512-0.7','512-0.8'}, dataVC, []);
                              
                s.store(['3.CBC-perf-DS' int2str(i)]);
            end
        end
        
        function fig4(s)
            
            % MTI accuracy
            
            rawData = s.load(makePlots.raw{4});
            
            % get data for the baseline
            baselines = s.filter(s.load(makePlots.raw{1}), [{{'Pdisc', 0}} makePlots.ttFilters(1)]);
                        
            % one plot for each dataset
            for i=1:6
                data = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(i)]);
                
                baseline = s.filter(baselines,  makePlots.dsFilters(i));
                baseline = s.select({baseline}, 'pctCorrect'); 
                
                dataMTI = s.group(data, {'Ek', 'PprunMaxBoxes'});
                dataMTI = s.select(dataMTI, 'pctCorrect');
                
                makePlots.barPlot(dataMTI, makePlots.xLabels{3}, makePlots.yLabels{1}, ...
                                   {makePlots.ttNames{6}, makePlots.ttNames{1}}, [], ...
                                   {'1','4-200','4-400','4-600','4-800','16-200','16-400','16-600','16-800'}, ...
                                   baseline, []);
                s.store(['4.MTI-acc-DS' int2str(i)]);
            end
        end
        
        function fig5(s)
            
            % MTI performance
            
            rawData = s.load(makePlots.raw{4});
                        
            % one plot for each dataset
            for i=1:6
                data = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(i)]);
                
                dataMTI = s.group(data, {'Ek', 'PprunMaxBoxes'});
                dataMTI = s.select(dataMTI, 'classifierBuildTimeAvg');
                
                makePlots.barPlot(dataMTI, 'Ek-compression factor', makePlots.yLabels(2), ...
                                  makePlots.ttNames(6), [], ...
                                  {'1','4-200','4-400','4-600','4-800','16-200','16-400','16-600','16-800'}, [], []);
                s.store(['5.MTI-perf-DS' int2str(i)]);
            end
        end
        
        function fig6(s)
            
            % TMo accuracy
            rawData = s.load(makePlots.raw{6});
            
            % get data for the baseline
            baselines = s.filter(s.load(makePlots.raw{1}), [{{'Pdisc', 0}} makePlots.ttFilters(1)]);
           
            baseline4 = s.filter(baselines,  makePlots.dsFilters(4));
            baseline4 = s.select({baseline4}, 'pctCorrect'); %, [makePlots.ttNames{1} '-' makePlots.dsNames{4}]
            
            baseline5 = s.filter(baselines,  makePlots.dsFilters(5));
            baseline5 = s.select({baseline5}, 'pctCorrect'); %, [makePlots.ttNames{1} '-' makePlots.dsNames{5}]
            
            dataTMO1 = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(1)]);
            dataTMO1 = s.group(dataTMO1, {'PprunMaxBoxes'});
            dataTMO1 = s.select(dataTMO1, 'pctCorrect');

            dataTMO2 = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(2)]);
            dataTMO2 = s.group(dataTMO2, {'PprunMaxBoxes'});
            dataTMO2 = s.select(dataTMO2, 'pctCorrect');
            
            dataTMO3 = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(3)]);
            dataTMO3 = s.group(dataTMO3, {'PprunMaxBoxes'});
            dataTMO3 = s.select(dataTMO3, 'pctCorrect');
            
            dataTMO4 = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(4)]);
            dataTMO4 = s.group(dataTMO4, {'PprunMaxBoxes'});
            dataTMO4 = s.select(dataTMO4, 'pctCorrect');
            
            dataTMO5 = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(5)]);
            dataTMO5 = s.group(dataTMO5, {'PprunMaxBoxes'});
            dataTMO5 = s.select(dataTMO5, 'pctCorrect');

            makePlots.linePlot([dataTMO1 dataTMO2 dataTMO3 dataTMO4 dataTMO5], makePlots.xLabels{3}, ...
                               makePlots.yLabels{1}, {makePlots.dsNames{1}, makePlots.dsNames{2}, ...
                               makePlots.dsNames{3}, makePlots.dsNames{4}, makePlots.dsNames{5}}, [], ...
                               {'200','400','600','800','1000','2000','3000','4000','5000','7500','10000'}, ...
                               [], []);
            s.store('6.TMo-acc-D45');
        end
        
        function fig7(s)
            
            % TMo performance
            
            rawData = s.load(makePlots.raw{5});
                        
            % one plot for each dataset
            for i=1:6
                data = s.filter(rawData, [{{'Pdisc', 0}} makePlots.dsFilters(i)]);
                
                dataTMO = s.group(data, {'Ek', 'PprunMaxBoxes'});
                dataTMO = s.select(dataTMO, 'classifierBuildTimeAvg');
                
                makePlots.barPlot(dataTMO, 'Ek-compression factor', makePlots.yLabels(2), ...
                                  makePlots.ttNames(5), [], ...
                                  {'1','4-200','4-400','4-600','4-800','16-200','16-400','16-600','16-800'}, [], []);
                s.store(['7.TMo-perf-D' int2str(i)]);
            end
        end
    end
    
    methods (Static = true)
        
        function run()
            
            plotter = makePlots();
            
            for i=9:9
                
                toMake = i;
                switch toMake
                    case 1
                        plotter.fig1();
                    case 2
                        plotter.fig2a();
                    case 3
                        plotter.fig3();
                    case 4
                        plotter.fig4();
                    case 5
                        plotter.fig5();
                    case 6
                        plotter.fig6();
                    case 7
                        plotter.fig7();
                    case 8
                        plotter.fig2b();
                    case 9
                        plotter.fig14();
                end
            end
        end
        
        function linePlot(data, xLabel, yLabel, legendLabels, axisScaling, xaxis, baseline, plotTitle)

            % some style properties
            set(0, 'DefaultAxesFontSize', makePlots.figFontSize);
            set(0, 'DefaultAxesFontName', 'Times');
            colormap(makePlots.defaultColormap);  
            
            % set bar labels
            set(gca,'XTickLabel', xaxis)
            set(gca,'XTick', 1:1:length(xaxis))
            
            if (~isempty(axisScaling))
                axis(axisScaling);
            end
            
            hold on;
            
            % add a title, if available
            if (~isempty(plotTitle))
                title(plotTitle);
            end
            
            % do the plotting
            plot(data(:,1), '--^', 'Color', makePlots.defaultColormap(1,:));
            plot(data(:,2), '--o', 'Color', makePlots.defaultColormap(16,:));
            plot(data(:,3), '--*', 'Color', makePlots.defaultColormap(32,:));
            plot(data(:,4), '--square', 'Color', makePlots.defaultColormap(48,:));
            plot(data(:,5), '--diamond', 'Color', makePlots.defaultColormap(64,:));
            
            % plot the baseline if any
            if (~isempty(baseline))
                
                if (length(baseline) > 1)
                    
                    baselineX = 1:1:length(xaxis);
                    if (length(data) > length(baseline))
                        baselineY = [];
                        for i=1:length(baseline)
                            baselineY = [baselineY repmat(baseline(i), 1, length(data)/length(baseline))];
                        end
                    else
                        baselineY = baseline;
                    end
                else
                    
                    baselineX = 0:1:length(xaxis)+1;
                    baselineY = repmat(baseline, 1, length(xaxis)+2);
                end

                plot(baselineX, baselineY, 'x', 'MarkerSize', 5, 'MarkerFaceColor', 'b');
            end
            
            % labeling
            if (~isempty(xLabel))
                xlabel (xLabel,'FontSize',16,'FontName','Times');
            end
            if (~isempty(yLabel))
                ylabel (yLabel,'FontSize',16,'FontName','Times');
            end
            
            if (~isempty(legendLabels)) 
                legend(legendLabels, 'Position', [.15,.4,.2,.1], 'FontSize', 10); % 'Location', 'East', 
            end
            
            hold off;
        end
        
        function barPlot(data, xLabel, yLabel, legendLabels, axisScaling, xaxis, baseline, plotTitle)

            % some style properties
            set(0, 'DefaultAxesFontSize', makePlots.figFontSize);
            set(0, 'DefaultAxesFontName', 'Times');
            colormap(makePlots.defaultColormap);  
            
            % set bar labels
            %set(gca,'XTickLabel', xaxis) % replaced by my_xticklabel
            set(gca,'XTick', 1:1:length(xaxis))
            
            if (~isempty(axisScaling))
                axis(axisScaling);
            end
            
            hold on;
            
            % add a title, if available
            if (~isempty(plotTitle))
                title(plotTitle,'FontSize',24);
            end
            
            % do the plotting
            bar(data);
            
            % plot the baseline if any
            if (~isempty(baseline))
                
                if (length(baseline) > 1)
                    
                    baselineX = 1:1:length(xaxis);
                    if (length(data) > length(baseline))
                        baselineY = [];
                        for i=1:length(baseline)
                            baselineY = [baselineY repmat(baseline(i), 1, length(data)/length(baseline))];
                        end
                    else
                        baselineY = baseline;
                    end
                else
                    
                    baselineX = 0:1:length(xaxis)+1;
                    baselineY = repmat(baseline, 1, length(xaxis)+2);
                end

                plot(baselineX, baselineY, '^r', 'MarkerSize', 12, 'MarkerFaceColor', 'r');
            end
            
            % labeling
            if (~isempty(xLabel))
                xLab = xlabel (xLabel,'FontSize',20,'FontName','Times');
                position = get(xLab, 'Position');
                %position(1) = 0.5 + position(1);    % shift down position of the x-axis labels.
                set(xLab, 'Position', position);
            end
            if (~isempty(yLabel))
                ylabel (yLabel,'FontSize',18,'FontName','Times');
            end
            
            if (~isempty(legendLabels)) 
                legend(legendLabels, 'Location','East','FontSize',18);
            end
            
            % set bar labels
            my_xticklabels(1:1:length(xaxis), xaxis,'FontSize',18);
            
            hold off;
        end
    end
end
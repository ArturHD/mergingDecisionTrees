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


function [ intersections ] = intersectCubes( cubesA, cubesB )
% Finds intersections of ncubes in cell array cubesA with ncubes in cell array 2
% notation is [ U1 U2 ... UN ] 
%             [ L1 L2 ....LN ] for a single ncube

dimensions = size(cubesA{1,1})(1);

intervalsA = projectIntervals(cubesA, 1);
intervalsB = projectIntervals(cubesB, 1);
intersections = intersectIntervals(intervalsA, intervalsB);

for i = 2:dimensions
	intervalsA = projectIntervals(cubesA, i);
	intervalsB = projectIntervals(cubesB, i);
	intersections = intersections & intersectIntervals(intervalsA, intervalsB);
end

function [intersections] = intersectIntervals( intervalsA, intervalsB)
% Finds intersections of intervals in intervalsA with intervals in intervalsB. Notation as projectIntevals
% SEE projectIntervals

events = [[intervalsA; ones(1, size(intervalsA)(2))],[intervalsB; 2(ones(1, size(intervalsB)(2))) ]]; % Merge both intervals, recording source in (4,:)
[s, i] = sort(events(1,:)); % TODO: Sort on event coordinate first, then event type to handle adjacent intervals right
events = events(:,i);

intersections = sparse(size(intervalsA)(2) /2, size(intervalsB)(2)/2);

openA = [];
openB = [];

for i = 1:size(events)(2)
	event = events(:,i);
	eventType = event(2);
	cubeIndex = event(3);
	source = event(4);
	
        if eventType == 0
		if source == 1
			openA = [openA, cubeIndex];
			intersections(cubeIndex, openB) = 1;
		else
			openB = [openB, cubeIndex];
			intersections(openA, cubeIndex) = 1;
		end
	else
		if source == 1
			openA(openA == cubeIndex) = [];
		else
			openB(openB == cubeIndex) = [];
		end
	end
end

function [ intervals ] = projectIntervals(cubes, dim)
% Returns the projection of the supplied ncubes onto the dim dimension
% Output consists of columns [ x, t, i] where
% x is the interval endpoint coordinate,
% t signifies the lower endpoint when zero and upper endpoint when one,
% i the index of the cube

intervals = [];

for i = 1:length(cubes)
	cube = cubes{1,i}; % or just cubes{i} as CA cubes is 1-dimensional?
	intervals = [intervals, [cube(2, dim); 0; i], [cube(1, dim); 1; i]];
end

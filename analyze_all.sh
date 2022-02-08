#!/bin/bash

mkdir -p logs

for problemDir in mini_Project_CodeNet/p*
do
    problemName="$(basename $problemDir)"
    echo "Analyzing $problemDir"
    inputFile="$(realpath ../Project_CodeNet/derived/input_output/data/$problemName/input.txt)"
    for solutionFile in $problemDir/Java/s*.java
    do
        echo "Analyzing $solutionFile"
        solutionFileName="$(basename $solutionFile)"
        logFileName="logs/$(basename $problemDir)-${solutionFileName%.java}.csv"
        ./analyze "$solutionFile" -o $(realpath $logFileName) < $inputFile
    done
done

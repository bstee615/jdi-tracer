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
        logFilePath="$(realpath logs/$(basename $problemDir)-${solutionFileName%.java}.csv)"
        time (timeout 30s ./analyze "$solutionFile" -o "$logFilePath" < $inputFile)
        exitCode="$?"
        echo "exitCode=$exitCode for $solutionFile"
    done
done

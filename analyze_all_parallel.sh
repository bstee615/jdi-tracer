#!/bin/bash

mkdir -p logs

echo_var() {
    solutionFile="$1"
    problemDir=$(dirname $(dirname $solutionFile))
    inputFile="$2"
    # echo "Analyzing $solutionFile"
    solutionFileName="$(basename $solutionFile)"
    logFilePath="$(realpath logs/$(basename $problemDir)-${solutionFileName%.java}.csv)"
    echo "***analyzing*** $solutionFile"
    time (
        timeout 30s ./analyze "$solutionFile" -o "$logFilePath" 2>&1 < $inputFile;
        exitCode="$?";
        echo "***exitCode=$exitCode*** $solutionFile";
    )
    return 0
}
export -f echo_var

for problemDir in mini_Project_CodeNet/p*
do
    problemName="$(basename $problemDir)"
    echo "Analyzing $problemDir"
    inputFile="$(realpath ../Project_CodeNet/derived/input_output/data/$problemName/input.txt)"
    # https://stackoverflow.com/a/50351932/8999671
    ls $problemDir/Java/s*.java | parallel --group -I% --max-args 1 -P 10 echo_var % $inputFile {}
    break
done

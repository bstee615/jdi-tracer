#!/bin/bash

trap "echo Analysis interrupted!; exit;" SIGINT SIGTERM

mkdir -p logs outputs
rm -f output.txt

echo_var() {
    solutionFile="$1"
    problemDir=$(dirname $(dirname $solutionFile))
    inputFile="$2"
    # echo "Analyzing $solutionFile"
    solutionFileName="$(basename $solutionFile)"
    run_id="java_$(basename $problemDir)_${solutionFileName%.java}"
    logFilePath="$(realpath logs/$run_id.xml)"
    outputFilePath="$(realpath outputs/$run_id.txt)"
    echo "***analyzing*** $solutionFile"
    time (
        begin="$(date +%s.%3N)"
        echo "$run_id begin: $begin seconds"

        timeout 10s ./analyze "$solutionFile" -l "$logFilePath" -o "$outputFilePath" 2>&1 < $inputFile;
        exitCode="$?";
        echo "$run_id exit code: $exitCode";
        
        end="$(date +%s.%3N)"
        echo "$run_id end: $end seconds"
        elapsed="$(echo "scale=3; $end - $begin" | bc)"
        echo "$run_id elapsed: $elapsed seconds"
    )
    return 0
}
export -f echo_var

for problemDir in ../Project_CodeNet/mini/p*
do
    problemName="$(basename $problemDir)"
    echo "***analyzing problem*** $problemDir"
    inputFile="$(realpath ../Project_CodeNet/derived/input_output/data/$problemName/input.txt)"
    # https://stackoverflow.com/a/50351932/8999671
    ls $problemDir/Java/s*.java | parallel --group -I% --max-args 1 -P 6 echo_var % $inputFile {} &>> output.txt
done

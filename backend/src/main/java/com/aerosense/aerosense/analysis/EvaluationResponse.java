package com.aerosense.aerosense.analysis;

public record EvaluationResponse(
    String modelName,
    String modelVersion,
    int totalCycles,
    int truePositives,
    int falsePositives,
    int trueNegatives,
    int falseNegatives,
    double precision,
    double recall,
    double f1,
    String disclaimer) {}

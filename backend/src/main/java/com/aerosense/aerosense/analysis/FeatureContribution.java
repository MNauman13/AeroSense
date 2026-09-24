package com.aerosense.aerosense.analysis;

public record FeatureContribution(
    String featureName,
    double observedValue,
    double baselineMedian,
    double robustZScore,
    double contribution) {}

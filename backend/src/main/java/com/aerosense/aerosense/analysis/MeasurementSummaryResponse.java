package com.aerosense.aerosense.analysis;

public record MeasurementSummaryResponse(
    String featureName,
    String unit,
    long sampleCount,
    double average,
    double minimum,
    double maximum) {}

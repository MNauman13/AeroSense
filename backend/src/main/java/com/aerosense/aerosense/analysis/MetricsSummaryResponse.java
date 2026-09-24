package com.aerosense.aerosense.analysis;

import java.util.List;

public record MetricsSummaryResponse(
    long totalCycles,
    long analyzedCycles,
    long flaggedCycles,
    List<MeasurementSummaryResponse> measurements,
    String disclaimer) {}

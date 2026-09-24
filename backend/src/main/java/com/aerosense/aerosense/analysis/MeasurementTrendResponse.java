package com.aerosense.aerosense.analysis;

import java.util.List;

public record MeasurementTrendResponse(
    String featureName, String unit, List<MeasurementTrendPoint> points, String disclaimer) {}

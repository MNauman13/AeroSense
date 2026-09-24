package com.aerosense.aerosense.cycle;

import java.time.Instant;

public record MeasurementResponse(
    String featureName, double value, String unit, Instant measuredAt) {}

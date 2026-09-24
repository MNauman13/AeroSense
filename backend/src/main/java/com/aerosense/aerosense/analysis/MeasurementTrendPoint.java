package com.aerosense.aerosense.analysis;

import java.time.Instant;
import java.util.UUID;

public record MeasurementTrendPoint(
    UUID cycleId, String cycleCode, Instant recordedAt, double value) {}

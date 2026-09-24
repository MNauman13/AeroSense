package com.aerosense.aerosense.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisRunResponse(
    UUID id,
    Instant startedAt,
    Instant completedAt,
    String modelName,
    String modelVersion,
    String status,
    double threshold,
    int cycleCount,
    String errorMessage) {}

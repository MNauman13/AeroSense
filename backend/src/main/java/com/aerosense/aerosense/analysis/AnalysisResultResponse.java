package com.aerosense.aerosense.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResultResponse(
    UUID id,
    UUID analysisRunId,
    UUID cycleId,
    double score,
    double threshold,
    boolean isFlagged,
    Explanation explanation,
    Instant createdAt) {}

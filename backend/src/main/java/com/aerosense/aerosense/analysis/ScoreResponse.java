package com.aerosense.aerosense.analysis;

import java.util.List;

public record ScoreResponse(
    String modelName,
    String modelVersion,
    double threshold,
    String scoreDirection,
    List<ScoredCycle> results) {}

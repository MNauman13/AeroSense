package com.aerosense.aerosense.analysis;

import java.util.UUID;

public record ScoredCycle(UUID cycleId, double score, boolean isFlagged, Explanation explanation) {}

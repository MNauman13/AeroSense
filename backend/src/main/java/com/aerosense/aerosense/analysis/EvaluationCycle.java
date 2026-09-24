package com.aerosense.aerosense.analysis;

import java.util.Map;
import java.util.UUID;

public record EvaluationCycle(UUID cycleId, Map<String, Double> features, boolean syntheticLabel) {}

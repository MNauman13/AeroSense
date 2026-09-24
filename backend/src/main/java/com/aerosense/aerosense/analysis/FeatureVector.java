package com.aerosense.aerosense.analysis;

import java.util.Map;
import java.util.UUID;

public record FeatureVector(UUID cycleId, Map<String, Double> features) {}

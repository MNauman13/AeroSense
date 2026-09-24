package com.aerosense.aerosense.analysis;

import java.util.List;

public record ScoreRequest(Double threshold, List<FeatureVector> cycles) {}

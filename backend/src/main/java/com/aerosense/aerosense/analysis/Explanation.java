package com.aerosense.aerosense.analysis;

import java.util.List;

public record Explanation(String method, List<FeatureContribution> features) {}

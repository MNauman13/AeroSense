package com.aerosense.aerosense.analysis;

import java.util.List;

public record EvaluationRequest(Double threshold, List<EvaluationCycle> cycles) {}

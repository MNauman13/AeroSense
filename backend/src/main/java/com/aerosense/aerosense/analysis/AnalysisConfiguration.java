package com.aerosense.aerosense.analysis;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record AnalysisConfiguration(@DecimalMin("0.0") @DecimalMax("1.0") Double threshold) {}

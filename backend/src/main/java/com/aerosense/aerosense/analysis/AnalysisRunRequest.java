package com.aerosense.aerosense.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record AnalysisRunRequest(
    @NotBlank @Pattern(regexp = "robust-zscore") String modelName,
    UUID rigId,
    Instant from,
    Instant to,
    @Valid AnalysisConfiguration configuration) {}

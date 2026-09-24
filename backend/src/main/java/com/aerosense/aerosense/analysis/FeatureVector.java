package com.aerosense.aerosense.analysis;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record FeatureVector(
    @NotNull UUID cycleId, @NotEmpty Map<@NotNull String, @NotNull Double> features) {}

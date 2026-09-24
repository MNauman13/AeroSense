package com.aerosense.aerosense.cycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CycleDetailResponse(
    UUID id,
    String cycleCode,
    UUID rigId,
    Instant recordedAt,
    String cycleType,
    Boolean isFlagged,
    List<MeasurementResponse> measurements) {}

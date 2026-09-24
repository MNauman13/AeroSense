package com.aerosense.aerosense.cycle;

import java.time.Instant;
import java.util.UUID;

public record CycleResponse(
    UUID id,
    String cycleCode,
    UUID rigId,
    Instant recordedAt,
    String cycleType,
    Boolean isFlagged) {}

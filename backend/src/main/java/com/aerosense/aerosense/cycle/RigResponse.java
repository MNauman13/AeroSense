package com.aerosense.aerosense.cycle;

import java.util.UUID;

public record RigResponse(UUID id, String rigCode, String description, String syntheticNotice) {}

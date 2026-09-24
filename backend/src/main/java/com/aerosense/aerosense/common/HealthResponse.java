package com.aerosense.aerosense.common;

import java.util.Map;

public record HealthResponse(String status, Map<String, String> dependencies) {}

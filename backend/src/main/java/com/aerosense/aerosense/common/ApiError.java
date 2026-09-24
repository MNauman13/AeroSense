package com.aerosense.aerosense.common;

import java.util.List;
import java.util.Map;

public record ApiError(
    String code,
    String message,
    String requestId,
    Map<String, List<String>> fieldErrors) {}

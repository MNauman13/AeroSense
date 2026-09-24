package com.aerosense.aerosense.assistant;

import java.util.Map;
import java.util.UUID;

public record RetrievalRequest(String question, UUID cycleId, Map<String, Object> cycleContext) {}

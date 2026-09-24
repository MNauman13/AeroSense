package com.aerosense.aerosense.assistant;

import java.util.UUID;

public record QuestionRequest(String question, UUID cycleId) {}

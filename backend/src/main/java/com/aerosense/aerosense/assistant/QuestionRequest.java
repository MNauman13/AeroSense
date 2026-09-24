package com.aerosense.aerosense.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record QuestionRequest(@NotBlank @Size(max = 500) String question, UUID cycleId) {}

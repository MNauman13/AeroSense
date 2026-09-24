package com.aerosense.aerosense.assistant;

import java.util.List;

public record AnswerResponse(
    String answer, boolean insufficientEvidence, List<Citation> citations, String disclaimer) {}

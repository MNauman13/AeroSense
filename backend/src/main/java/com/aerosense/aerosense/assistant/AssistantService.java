package com.aerosense.aerosense.assistant;

import com.aerosense.aerosense.analysis.AnomalyResultRepository;
import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleEntity;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AssistantService {

  private static final String SYNTHETIC_DISCLAIMER =
      "Synthetic demonstration only. Not engineering or maintenance advice.";

  private final TestCycleRepository cycleRepository;
  private final MeasurementRepository measurementRepository;
  private final AnomalyResultRepository resultRepository;
  private final RetrievalClient retrievalClient;
  private final ObjectMapper objectMapper;

  public AssistantService(
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository,
      AnomalyResultRepository resultRepository,
      RetrievalClient retrievalClient,
      ObjectMapper objectMapper) {
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
    this.resultRepository = resultRepository;
    this.retrievalClient = retrievalClient;
    this.objectMapper = objectMapper;
  }

  public AnswerResponse answer(QuestionRequest request) {
    Map<String, Object> cycleContext =
        request.cycleId() == null ? null : loadCycleContext(request.cycleId());
    AnswerResponse response =
        retrievalClient.answer(
            new RetrievalRequest(request.question(), request.cycleId(), cycleContext));
    if (response == null
        || response.answer() == null
        || response.disclaimer() == null
        || (!response.insufficientEvidence()
            && (response.citations() == null || response.citations().isEmpty()))) {
      throw new RetrievalUnavailableException(
          new IllegalStateException("Retrieval service returned an incomplete answer."));
    }
    return response;
  }

  private Map<String, Object> loadCycleContext(UUID cycleId) {
    TestCycleEntity cycle =
        cycleRepository
            .findById(cycleId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Synthetic cycle not found."));
    Map<String, Object> context = new HashMap<>();
    context.put("cycleCode", cycle.getCycleCode());
    context.put("cycleType", cycle.getCycleType());
    context.put("recordedAt", cycle.getRecordedAt().toString());
    Map<String, Double> features = new HashMap<>();
    measurementRepository.findAllByCycle_IdOrderByFeatureNameAsc(cycleId).stream()
        .forEach(measurement -> features.put(measurement.getFeatureName(), measurement.getValue()));
    context.put("features", features);
    resultRepository
        .findFirstByTestCycle_IdOrderByCreatedAtDesc(cycleId)
        .ifPresent(
            result -> {
              Map<String, Object> latest = new HashMap<>();
              latest.put("score", result.getScore());
              latest.put("threshold", result.getThreshold());
              latest.put("isFlagged", result.isFlagged());
              latest.put(
                  "explanation",
                  objectMapper.convertValue(result.getExplanationJson(), new TypeReference<>() {}));
              context.put("latestAnalysis", latest);
            });
    return context;
  }
}

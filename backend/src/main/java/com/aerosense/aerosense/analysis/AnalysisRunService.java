package com.aerosense.aerosense.analysis;

import com.aerosense.aerosense.common.PageResponse;
import com.aerosense.aerosense.cycle.MeasurementEntity;
import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleEntity;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AnalysisRunService {

  private static final String MODEL_NAME = "robust-zscore";
  private static final double DEFAULT_THRESHOLD = 0.65;
  private static final int MAX_ANALYSIS_CYCLES = 10_000;
  private static final String SYNTHETIC_NOTICE =
      "Synthetic demonstration only. Not engineering or maintenance advice.";
  private static final List<String> FEATURE_NAMES =
      List.of(
          "extension_time_ms",
          "pressure_kpa",
          "vibration_rms",
          "temperature_c",
          "cycle_duration_ms");

  private final AnalysisRunRepository runRepository;
  private final AnomalyResultRepository resultRepository;
  private final TestCycleRepository cycleRepository;
  private final MeasurementRepository measurementRepository;
  private final AnalyticsClient analyticsClient;
  private final ObjectMapper objectMapper;

  public AnalysisRunService(
      AnalysisRunRepository runRepository,
      AnomalyResultRepository resultRepository,
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository,
      AnalyticsClient analyticsClient,
      ObjectMapper objectMapper) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
    this.analyticsClient = analyticsClient;
    this.objectMapper = objectMapper;
  }

  public AnalysisRunResponse create(AnalysisRunRequest request) {
    validateTimeRange(request.from(), request.to());
    double threshold =
        request.configuration() == null || request.configuration().threshold() == null
            ? DEFAULT_THRESHOLD
            : request.configuration().threshold();

    Page<TestCycleEntity> selectedPage =
        cycleRepository.findFiltered(
            request.rigId(),
            request.from(),
            request.to(),
            null,
            PageRequest.of(0, MAX_ANALYSIS_CYCLES + 1, Sort.unsorted()));
    if (selectedPage.getTotalElements() > MAX_ANALYSIS_CYCLES) {
      throw new IllegalArgumentException("An analysis run can include at most 10000 cycles.");
    }
    List<TestCycleEntity> cycles = selectedPage.getContent();
    if (cycles.isEmpty()) {
      throw new IllegalArgumentException(
          "Analysis filters must select at least one synthetic cycle.");
    }

    Map<UUID, Map<String, Double>> featuresByCycle = loadFeatureVectors(cycles);
    List<FeatureVector> vectors =
        cycles.stream()
            .map(cycle -> new FeatureVector(cycle.getId(), featuresByCycle.get(cycle.getId())))
            .toList();

    Instant startedAt = Instant.now();
    UUID runId = UUID.randomUUID();
    Map<String, Object> config =
        Map.of(
            "threshold", threshold,
            "cycleCount", cycles.size(),
            "featureNames", FEATURE_NAMES,
            "syntheticNotice", SYNTHETIC_NOTICE);
    AnalysisRunEntity run =
        runRepository.save(
            new AnalysisRunEntity(runId, startedAt, MODEL_NAME, "1", config, "RUNNING"));

    ScoreResponse scored;
    try {
      scored = analyticsClient.score(new ScoreRequest(threshold, vectors));
      validateScoreResponse(scored, cycles, threshold);
    } catch (AnalyticsUnavailableException | IllegalStateException exception) {
      run.fail(
          Instant.now(),
          "Synthetic analysis failed. Check that the analytics service is available.");
      runRepository.save(run);
      return toRunResponse(run, cycles.size());
    }

    run.setModelVersion(scored.modelName(), scored.modelVersion());
    Instant completedAt = Instant.now();
    List<AnomalyResultEntity> results = new ArrayList<>();
    Map<UUID, TestCycleEntity> cyclesById = new HashMap<>();
    cycles.forEach(cycle -> cyclesById.put(cycle.getId(), cycle));
    for (ScoredCycle score : scored.results()) {
      Map<String, Object> explanation =
          objectMapper.convertValue(score.explanation(), new TypeReference<>() {});
      results.add(
          new AnomalyResultEntity(
              UUID.randomUUID(),
              run,
              cyclesById.get(score.cycleId()),
              score.score(),
              scored.threshold(),
              score.isFlagged(),
              explanation,
              completedAt));
    }
    resultRepository.saveAll(results);
    run.succeed(completedAt);
    runRepository.save(run);
    return toRunResponse(run, cycles.size());
  }

  @Transactional(readOnly = true)
  public AnalysisRunResponse getRun(UUID runId) {
    AnalysisRunEntity run = getRequiredRun(runId);
    int count = Math.toIntExact(resultRepository.countByAnalysisRun_Id(runId));
    if (count == 0 && "FAILED".equals(run.getStatus())) {
      count = ((Number) run.getConfigJson().getOrDefault("cycleCount", 0)).intValue();
    }
    return toRunResponse(run, count);
  }

  @Transactional(readOnly = true)
  public PageResponse<AnalysisResultResponse> getResults(
      UUID runId, Boolean flagged, int page, int size) {
    validatePage(page, size);
    AnalysisRunEntity run = getRequiredRun(runId);
    if (!"SUCCEEDED".equals(run.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Analysis run has no completed results.");
    }
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "score").and(Sort.by("id")));
    Page<AnomalyResultEntity> resultPage =
        flagged == null
            ? resultRepository.findByAnalysisRun_Id(runId, pageable)
            : resultRepository.findByAnalysisRun_IdAndIsFlagged(runId, flagged, pageable);
    return new PageResponse<>(
        resultPage.getContent().stream().map(this::toResultResponse).toList(),
        page,
        size,
        resultPage.getTotalElements());
  }

  @Transactional(readOnly = true)
  public EvaluationResponse evaluate(UUID runId) {
    AnalysisRunEntity run = getRequiredRun(runId);
    if (!"SUCCEEDED".equals(run.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only completed runs can be evaluated.");
    }
    List<AnomalyResultEntity> results = resultRepository.findAllByAnalysisRun_Id(runId);
    List<UUID> cycleIds = results.stream().map(result -> result.getTestCycle().getId()).toList();
    Map<UUID, Map<String, Double>> features = loadFeatureVectorsByIds(cycleIds);
    List<EvaluationCycle> evaluationCycles =
        results.stream()
            .map(
                result ->
                    new EvaluationCycle(
                        result.getTestCycle().getId(),
                        features.get(result.getTestCycle().getId()),
                        result.getTestCycle().isSyntheticLabel()))
            .toList();
    double threshold = ((Number) run.getConfigJson().get("threshold")).doubleValue();
    EvaluationResponse response =
        analyticsClient.evaluate(new EvaluationRequest(threshold, evaluationCycles));
    if (response == null || response.totalCycles() != results.size()) {
      throw new AnalyticsUnavailableException(
          new IllegalStateException("Analytics returned an incomplete evaluation response."));
    }
    return response;
  }

  @Transactional(readOnly = true)
  public AnalysisResultResponse getLatestForCycle(UUID cycleId) {
    if (!cycleRepository.existsById(cycleId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Synthetic cycle not found.");
    }
    AnomalyResultEntity result =
        resultRepository
            .findFirstByTestCycle_IdOrderByCreatedAtDesc(cycleId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No completed analysis exists for this synthetic cycle."));
    return toResultResponse(result);
  }

  @Transactional(readOnly = true)
  public MetricsSummaryResponse getSummary() {
    List<MeasurementSummaryResponse> summaries =
        measurementRepository.summarizeByFeature().stream()
            .map(
                row ->
                    new MeasurementSummaryResponse(
                        row.getFeatureName(),
                        row.getUnit(),
                        row.getSampleCount(),
                        row.getAverage(),
                        row.getMinimum(),
                        row.getMaximum()))
            .toList();
    return new MetricsSummaryResponse(
        cycleRepository.count(),
        resultRepository.countAnalyzedCycles(),
        resultRepository.countLatestFlaggedCycles(),
        summaries,
        "Synthetic demonstration data only. Summary statistics are not engineering limits or safety advice.");
  }

  private Map<UUID, Map<String, Double>> loadFeatureVectors(List<TestCycleEntity> cycles) {
    return loadFeatureVectorsByIds(cycles.stream().map(TestCycleEntity::getId).toList());
  }

  private Map<UUID, Map<String, Double>> loadFeatureVectorsByIds(List<UUID> cycleIds) {
    Map<UUID, Map<String, Double>> features = new HashMap<>();
    List<MeasurementEntity> measurements =
        measurementRepository.findAllByCycle_IdInOrderByCycle_IdAscFeatureNameAsc(cycleIds);
    for (MeasurementEntity measurement : measurements) {
      features
          .computeIfAbsent(measurement.getCycle().getId(), ignored -> new HashMap<>())
          .put(measurement.getFeatureName(), measurement.getValue());
    }
    for (UUID cycleId : cycleIds) {
      Map<String, Double> vector = features.get(cycleId);
      if (vector == null || !vector.keySet().equals(new HashSet<>(FEATURE_NAMES))) {
        throw new IllegalStateException(
            "Synthetic cycle does not contain the complete feature set.");
      }
    }
    return features;
  }

  private void validateScoreResponse(
      ScoreResponse response, List<TestCycleEntity> cycles, double expectedThreshold) {
    if (response == null
        || !MODEL_NAME.equals(response.modelName())
        || response.modelVersion() == null
        || response.modelVersion().isBlank()
        || response.threshold() != expectedThreshold
        || response.results() == null) {
      throw new IllegalStateException("Analytics returned an invalid score response.");
    }
    Set<UUID> expected = new HashSet<>(cycles.stream().map(TestCycleEntity::getId).toList());
    Set<UUID> returned = new HashSet<>();
    for (ScoredCycle score : response.results()) {
      if (score == null
          || score.cycleId() == null
          || !returned.add(score.cycleId())
          || !Double.isFinite(score.score())
          || score.score() < 0.0
          || score.score() > 1.0
          || score.isFlagged() != (score.score() >= expectedThreshold)
          || score.explanation() == null) {
        throw new IllegalStateException("Analytics returned an invalid score result.");
      }
      validateExplanation(score.explanation(), score.score());
    }
    if (!returned.equals(expected)) {
      throw new IllegalStateException("Analytics result IDs do not match the requested cohort.");
    }
  }

  private void validateExplanation(Explanation explanation, double score) {
    if (!"absolute_robust_z".equals(explanation.method())
        || explanation.features() == null
        || explanation.features().size() != FEATURE_NAMES.size()) {
      throw new IllegalStateException("Analytics returned an invalid explanation.");
    }
    Set<String> returnedFeatures = new HashSet<>();
    double maximumContribution = 0.0;
    for (FeatureContribution feature : explanation.features()) {
      if (feature == null
          || !FEATURE_NAMES.contains(feature.featureName())
          || !returnedFeatures.add(feature.featureName())
          || !Double.isFinite(feature.observedValue())
          || !Double.isFinite(feature.baselineMedian())
          || !Double.isFinite(feature.robustZScore())
          || !Double.isFinite(feature.contribution())
          || feature.contribution() < 0.0
          || feature.contribution() > 1.0) {
        throw new IllegalStateException("Analytics returned an invalid explanation feature.");
      }
      maximumContribution = Math.max(maximumContribution, feature.contribution());
    }
    if (!returnedFeatures.equals(new HashSet<>(FEATURE_NAMES))
        || Math.abs(maximumContribution - score) > 1e-12) {
      throw new IllegalStateException("Analytics explanation does not match the cycle score.");
    }
  }

  private AnalysisRunEntity getRequiredRun(UUID runId) {
    return runRepository
        .findById(runId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis run not found."));
  }

  private AnalysisRunResponse toRunResponse(AnalysisRunEntity run, int cycleCount) {
    double threshold = ((Number) run.getConfigJson().get("threshold")).doubleValue();
    return new AnalysisRunResponse(
        run.getId(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getModelName(),
        run.getModelVersion(),
        run.getStatus(),
        threshold,
        cycleCount,
        run.getErrorMessage());
  }

  private AnalysisResultResponse toResultResponse(AnomalyResultEntity result) {
    Explanation explanation =
        objectMapper.convertValue(result.getExplanationJson(), Explanation.class);
    return new AnalysisResultResponse(
        result.getId(),
        result.getAnalysisRun().getId(),
        result.getTestCycle().getId(),
        result.getScore(),
        result.getThreshold(),
        result.isFlagged(),
        explanation,
        result.getCreatedAt());
  }

  private void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
          "page must be non-negative and size must be between 1 and 100");
    }
  }

  private void validateTimeRange(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must be earlier than or equal to to");
    }
  }
}

package com.aerosense.aerosense.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aerosense.aerosense.cycle.MeasurementEntity;
import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleEntity;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.aerosense.aerosense.cycle.TestRigEntity;
import com.aerosense.aerosense.cycle.TestRigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalysisApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestRigRepository rigRepository;
  @Autowired private TestCycleRepository cycleRepository;
  @Autowired private MeasurementRepository measurementRepository;
  @Autowired private AnomalyResultRepository resultRepository;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AnalyticsClient analyticsClient;

  @Test
  void runPersistsScoresAndEvaluationUsesLabelsOnlyOnEvaluationRoute() throws Exception {
    TestRigEntity rig =
        rigRepository.save(
            new TestRigEntity(
                UUID.randomUUID(), "RIG-SYN-51", "Fictional synthetic demonstration rig."));
    TestCycleEntity cycleWithLabel = saveCycle(rig, "CYC-000051", true, 0);
    TestCycleEntity cycleWithoutLabel = saveCycle(rig, "CYC-000052", false, 1);
    when(analyticsClient.score(any(ScoreRequest.class)))
        .thenAnswer(
            invocation -> {
              ScoreRequest request = invocation.getArgument(0);
              assertThat(objectMapper.writeValueAsString(request)).doesNotContain("syntheticLabel");
              return new ScoreResponse(
                  "robust-zscore",
                  "1",
                  request.threshold(),
                  "higher_is_more_anomalous",
                  List.of(
                      scored(cycleWithLabel.getId(), 0.2, false),
                      scored(cycleWithoutLabel.getId(), 0.8, true)));
            });
    when(analyticsClient.evaluate(any(EvaluationRequest.class)))
        .thenAnswer(
            invocation -> {
              EvaluationRequest request = invocation.getArgument(0);
              assertThat(request.cycles())
                  .extracting(EvaluationCycle::syntheticLabel)
                  .containsExactly(true, false);
              return new EvaluationResponse(
                  "robust-zscore",
                  "1",
                  request.cycles().size(),
                  0,
                  1,
                  1,
                  0,
                  0.0,
                  0.0,
                  0.0,
                  "Synthetic evaluation only. Metrics do not generalize to real aircraft systems.");
            });

    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "modelName", "robust-zscore",
                "rigId", rig.getId(),
                "configuration", Map.of("threshold", 0.65)));
    String runResponse =
        mockMvc
            .perform(post("/api/v1/analysis-runs").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.cycleCount").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID runId = UUID.fromString(objectMapper.readTree(runResponse).get("id").asText());

    assertThat(resultRepository.countByAnalysisRun_Id(runId)).isEqualTo(2);
    mockMvc
        .perform(get("/api/v1/cycles").param("flagged", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(cycleWithoutLabel.getId().toString()));
    mockMvc
        .perform(get("/api/v1/cycles/{cycleId}/analysis/latest", cycleWithoutLabel.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFlagged").value(true))
        .andExpect(jsonPath("$.score").value(0.8));
    mockMvc
        .perform(get("/api/v1/analysis-runs/{runId}/results", runId).param("flagged", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mockMvc
        .perform(get("/api/v1/analysis-runs/{runId}/evaluation", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCycles").value(2));
    mockMvc
        .perform(get("/api/v1/metrics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCycles").value(2))
        .andExpect(jsonPath("$.analyzedCycles").value(2))
        .andExpect(jsonPath("$.flaggedCycles").value(1))
        .andExpect(jsonPath("$.measurements.length()").value(5));
    mockMvc
        .perform(
            get("/api/v1/metrics/summary")
                .param("rigId", rig.getId().toString())
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCycles").value(1))
        .andExpect(jsonPath("$.analyzedCycles").value(1))
        .andExpect(jsonPath("$.measurements[0].sampleCount").value(1));
  }

  @Test
  void unavailableAnalyticsProducesPersistedFailedRunWithSafeMessage() throws Exception {
    TestRigEntity rig =
        rigRepository.save(
            new TestRigEntity(
                UUID.randomUUID(), "RIG-SYN-53", "Fictional synthetic demonstration rig."));
    saveCycle(rig, "CYC-000053", false, 0);
    doThrow(
            new AnalyticsUnavailableException(
                new IllegalStateException("internal endpoint detail")))
        .when(analyticsClient)
        .score(any(ScoreRequest.class));
    String body =
        objectMapper.writeValueAsString(Map.of("modelName", "robust-zscore", "rigId", rig.getId()));

    String response =
        mockMvc
            .perform(post("/api/v1/analysis-runs").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.cycleCount").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID runId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(get("/api/v1/analysis-runs/{runId}", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(
            jsonPath("$.errorMessage")
                .value(
                    "Synthetic analysis failed because analytics was unavailable or returned invalid results."))
        .andExpect(
            jsonPath("$.errorMessage")
                .value(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal"))));
  }

  @Test
  void invalidAnalyticsResponseFailsRunWithoutSavingResultRows() throws Exception {
    TestRigEntity rig =
        rigRepository.save(
            new TestRigEntity(
                UUID.randomUUID(), "RIG-SYN-54", "Fictional synthetic demonstration rig."));
    saveCycle(rig, "CYC-000054", false, 0);
    when(analyticsClient.score(any(ScoreRequest.class)))
        .thenReturn(
            new ScoreResponse("unknown-model", "1", 0.65, "higher_is_more_anomalous", List.of()));
    String body =
        objectMapper.writeValueAsString(Map.of("modelName", "robust-zscore", "rigId", rig.getId()));

    String response =
        mockMvc
            .perform(post("/api/v1/analysis-runs").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID runId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    assertThat(resultRepository.countByAnalysisRun_Id(runId)).isZero();
  }

  private TestCycleEntity saveCycle(
      TestRigEntity rig, String code, boolean syntheticLabel, int offset) {
    Instant at = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(offset);
    TestCycleEntity cycle =
        cycleRepository.save(
            new TestCycleEntity(
                UUID.randomUUID(), code, rig, at, "synthetic-extension-check", syntheticLabel));
    measurementRepository.saveAll(
        List.of(
            new MeasurementEntity(cycle, "extension_time_ms", 180.0 + offset, "ms", at),
            new MeasurementEntity(cycle, "pressure_kpa", 1000.0 + offset, "kPa", at),
            new MeasurementEntity(cycle, "vibration_rms", 0.30 + offset, "g_rms", at),
            new MeasurementEntity(cycle, "temperature_c", 24.0 + offset, "degC", at),
            new MeasurementEntity(cycle, "cycle_duration_ms", 2800.0 + offset, "ms", at)));
    return cycle;
  }

  private ScoredCycle scored(UUID cycleId, double score, boolean isFlagged) {
    return new ScoredCycle(
        cycleId,
        score,
        isFlagged,
        new Explanation(
            "absolute_robust_z",
            List.of(
                new FeatureContribution("extension_time_ms", 180.0, 180.0, 0.0, score),
                new FeatureContribution("pressure_kpa", 1000.0, 1000.0, 0.0, 0.0),
                new FeatureContribution("vibration_rms", 0.3, 0.3, 0.0, 0.0),
                new FeatureContribution("temperature_c", 24.0, 24.0, 0.0, 0.0),
                new FeatureContribution("cycle_duration_ms", 2800.0, 2800.0, 0.0, 0.0))));
  }
}

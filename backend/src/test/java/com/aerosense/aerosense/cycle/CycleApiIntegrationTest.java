package com.aerosense.aerosense.cycle;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aerosense.aerosense.analysis.AnalysisRunEntity;
import com.aerosense.aerosense.analysis.AnalysisRunRepository;
import com.aerosense.aerosense.analysis.AnomalyResultEntity;
import com.aerosense.aerosense.analysis.AnomalyResultRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CycleApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestRigRepository rigRepository;
  @Autowired private TestCycleRepository cycleRepository;
  @Autowired private MeasurementRepository measurementRepository;
  @Autowired private AnalysisRunRepository analysisRunRepository;
  @Autowired private AnomalyResultRepository anomalyResultRepository;

  @Test
  void cyclesCanBeFilteredByRigAndInclusiveTimeWindowAndPaginated() throws Exception {
    TestRigEntity rig = saveRig("RIG-SYN-01");
    saveCycle(rig, "CYC-000001", "2026-01-01T00:00:00Z");
    TestCycleEntity selected = saveCycle(rig, "CYC-000002", "2026-01-02T00:00:00Z");
    saveRig("RIG-SYN-02");

    mockMvc
        .perform(
            get("/api/v1/cycles")
                .param("rigId", rig.getId().toString())
                .param("from", "2026-01-02T00:00:00Z")
                .param("to", "2026-01-02T00:00:00Z")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(selected.getId().toString()))
        .andExpect(jsonPath("$.items[0].isFlagged").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void cycleDetailIncludesMeasurementsAndOmitsGroundTruthLabel() throws Exception {
    TestRigEntity rig = saveRig("RIG-SYN-03");
    TestCycleEntity cycle = saveCycle(rig, "CYC-000003", "2026-01-03T00:00:00Z");
    measurementRepository.save(
        new MeasurementEntity(cycle, "vibration_rms", 0.31, "g_rms", cycle.getRecordedAt()));

    mockMvc
        .perform(get("/api/v1/cycles/{cycleId}", cycle.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cycleCode").value("CYC-000003"))
        .andExpect(jsonPath("$.measurements.length()").value(1))
        .andExpect(jsonPath("$.measurements[0].featureName").value("vibration_rms"))
        .andExpect(jsonPath("$.syntheticLabel").doesNotExist());
  }

  @Test
  void missingCycleAndInvalidRangeUseCommonClientErrors() throws Exception {
    mockMvc
        .perform(get("/api/v1/cycles/{cycleId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").exists());

    mockMvc
        .perform(
            get("/api/v1/cycles")
                .param("from", "2026-01-03T00:00:00Z")
                .param("to", "2026-01-02T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
  }

  @Test
  void flaggedFilterDoesNotUseSyntheticGroundTruth() throws Exception {
    TestRigEntity rig = saveRig("RIG-SYN-04");
    saveCycle(rig, "CYC-000004", "2026-01-04T00:00:00Z", true);

    mockMvc
        .perform(get("/api/v1/cycles").param("flagged", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void csvAndReportDownloadsRespectFiltersAndIncludeOnlySyntheticDemoResults() throws Exception {
    TestRigEntity selectedRig = saveRig("RIG-SYN-05");
    TestRigEntity otherRig = saveRig("RIG-SYN-06");
    TestCycleEntity flaggedCycle = saveCycle(selectedRig, "CYC-000051", "2026-01-05T00:00:00Z");
    TestCycleEntity clearCycle = saveCycle(selectedRig, "CYC-000052", "2026-01-06T00:00:00Z");
    TestCycleEntity outsideRange = saveCycle(selectedRig, "CYC-000053", "2026-01-07T00:00:00Z");
    TestCycleEntity otherRigCycle = saveCycle(otherRig, "CYC-000054", "2026-01-05T00:00:00Z");
    measurementRepository.save(
        new MeasurementEntity(
            flaggedCycle, "vibration_rms", 0.31, "g_rms", flaggedCycle.getRecordedAt()));
    measurementRepository.save(
        new MeasurementEntity(
            clearCycle, "vibration_rms", 0.22, "g_rms", clearCycle.getRecordedAt()));

    Instant analysisTime = Instant.parse("2026-01-08T00:00:00Z");
    AnalysisRunEntity analysisRun =
        analysisRunRepository.save(
            new AnalysisRunEntity(
                UUID.randomUUID(),
                analysisTime,
                "robust-zscore",
                "test",
                Map.of("threshold", 3.0),
                "SUCCEEDED"));
    saveResult(analysisRun, flaggedCycle, true, analysisTime);
    saveResult(analysisRun, clearCycle, false, analysisTime);
    saveResult(analysisRun, outsideRange, true, analysisTime);
    saveResult(analysisRun, otherRigCycle, true, analysisTime);

    mockMvc
        .perform(
            get("/api/v1/cycles/export")
                .param("rigId", selectedRig.getId().toString())
                .param("from", "2026-01-05T00:00:00Z")
                .param("to", "2026-01-06T00:00:00Z")
                .param("flagged", "true"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(
            header()
                .string("Content-Disposition", containsString("aerosense-synthetic-test-runs.csv")))
        .andExpect(content().string(containsString("test_run_code")))
        .andExpect(content().string(containsString("CYC-000051")))
        .andExpect(content().string(containsString("0.31")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CYC-000052"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CYC-000053"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CYC-000054"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("synthetic_label"))));

    mockMvc
        .perform(
            get("/api/v1/cycles/report")
                .param("rigId", selectedRig.getId().toString())
                .param("from", "2026-01-05T00:00:00Z")
                .param("to", "2026-01-06T00:00:00Z")
                .param("flagged", "true"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/plain"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", containsString("aerosense-synthetic-test-report.txt")))
        .andExpect(content().string(containsString("Matching test runs: 1")))
        .andExpect(content().string(containsString("Stands out in demo comparison: 1")))
        .andExpect(content().string(containsString("vibration_rms (g_rms): 1 readings")))
        .andExpect(content().string(containsString("Synthetic demonstration only.")));

    mockMvc
        .perform(
            get("/api/v1/cycles/export")
                .param("rigId", selectedRig.getId().toString())
                .param("from", "2026-01-05T00:00:00Z")
                .param("to", "2026-01-06T00:00:00Z")
                .param("flagged", "false"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("CYC-000052")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CYC-000051"))));
  }

  private void saveResult(
      AnalysisRunEntity analysisRun, TestCycleEntity cycle, boolean flagged, Instant createdAt) {
    anomalyResultRepository.save(
        new AnomalyResultEntity(
            UUID.randomUUID(),
            analysisRun,
            cycle,
            flagged ? 4.2 : 0.2,
            3.0,
            flagged,
            Map.of(),
            createdAt));
  }

  private TestRigEntity saveRig(String code) {
    return rigRepository.save(
        new TestRigEntity(
            UUID.randomUUID(), code, "Fictional synthetic demonstration rig for API tests."));
  }

  private TestCycleEntity saveCycle(TestRigEntity rig, String code, String recordedAt) {
    return saveCycle(rig, code, recordedAt, false);
  }

  private TestCycleEntity saveCycle(
      TestRigEntity rig, String code, String recordedAt, boolean syntheticLabel) {
    return cycleRepository.save(
        new TestCycleEntity(
            UUID.randomUUID(),
            code,
            rig,
            Instant.parse(recordedAt),
            "synthetic-extension-check",
            syntheticLabel));
  }
}

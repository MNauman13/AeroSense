package com.aerosense.aerosense.cycle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

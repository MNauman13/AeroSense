package com.aerosense.aerosense.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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
class AssistantApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestRigRepository rigRepository;
  @Autowired private TestCycleRepository cycleRepository;
  @Autowired private MeasurementRepository measurementRepository;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private RetrievalClient retrievalClient;

  @Test
  void questionPassesSyntheticCycleContextAndReturnsCitations() throws Exception {
    TestRigEntity rig =
        rigRepository.save(
            new TestRigEntity(
                UUID.randomUUID(), "RIG-SYN-61", "Fictional synthetic demonstration rig."));
    Instant recordedAt = Instant.parse("2026-01-01T00:00:00Z");
    TestCycleEntity cycle =
        cycleRepository.save(
            new TestCycleEntity(
                UUID.randomUUID(),
                "CYC-000061",
                rig,
                recordedAt,
                "synthetic-extension-check",
                true));
    measurementRepository.save(
        new MeasurementEntity(cycle, "vibration_rms", 0.3, "g_rms", recordedAt));
    when(retrievalClient.answer(any(RetrievalRequest.class)))
        .thenAnswer(
            invocation -> {
              RetrievalRequest request = invocation.getArgument(0);
              assertThat(request.cycleId()).isEqualTo(cycle.getId());
              String wireBody = objectMapper.writeValueAsString(request);
              assertThat(wireBody).contains("CYC-000061", "vibration_rms");
              assertThat(wireBody).doesNotContain("syntheticLabel");
              return new AnswerResponse(
                  "The fictional note discusses the synthetic vibration summary.",
                  false,
                  List.of(
                      new Citation(
                          "demo-note-02#chunk-01",
                          "Fictional demonstration review note",
                          "Compare the synthetic vibration summary with the generated cycles.")),
                  "Synthetic demonstration only. Not engineering or maintenance advice.");
            });

    mockMvc
        .perform(
            post("/api/v1/assistant/questions")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("question", "What about vibration?", "cycleId", cycle.getId()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.insufficientEvidence").value(false))
        .andExpect(jsonPath("$.citations[0].sourceId").value("demo-note-02#chunk-01"))
        .andExpect(
            jsonPath("$.disclaimer")
                .value("Synthetic demonstration only. Not engineering or maintenance advice."));
  }

  @Test
  void retrievalFailureReturnsSafeServiceUnavailableError() throws Exception {
    doThrow(new RetrievalUnavailableException(new IllegalStateException("private service detail")))
        .when(retrievalClient)
        .answer(any(RetrievalRequest.class));

    mockMvc
        .perform(
            post("/api/v1/assistant/questions")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(Map.of("question", "What does the note say?"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("RETRIEVAL_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Synthetic retrieval is temporarily unavailable."))
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
  }
}

package com.aerosense.aerosense.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.aerosense.aerosense.cycle.TestRigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "aerosense.demo-data-directory=src/test/resources/demo-fixtures")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoDataApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestRigRepository rigRepository;
  @Autowired private TestCycleRepository cycleRepository;
  @Autowired private MeasurementRepository measurementRepository;

  @Test
  void seedIsRepeatableAndBrowseDoesNotExposeGroundTruthLabels() throws Exception {
    mockMvc
        .perform(post("/api/v1/demo-data/seed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seeded").value(true))
        .andExpect(jsonPath("$.rigCount").value(1))
        .andExpect(jsonPath("$.cycleCount").value(1))
        .andExpect(
            jsonPath("$.syntheticNotice").value(org.hamcrest.Matchers.containsString("Synthetic")));

    mockMvc
        .perform(post("/api/v1/demo-data/seed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seeded").value(false))
        .andExpect(jsonPath("$.cycleCount").value(1));

    mockMvc
        .perform(get("/api/v1/cycles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].syntheticLabel").doesNotExist());

    mockMvc
        .perform(get("/api/v1/cycles/00000000-0000-4000-8000-000000000101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measurements.length()").value(5))
        .andExpect(jsonPath("$.syntheticLabel").doesNotExist());

    org.assertj.core.api.Assertions.assertThat(rigRepository.count()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(cycleRepository.count()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(measurementRepository.count()).isEqualTo(5);
  }
}

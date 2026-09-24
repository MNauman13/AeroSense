package com.aerosense.aerosense.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.aerosense.aerosense.cycle.TestRigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DemoDataServiceTest {

  @Test
  void repeatedSeedRequestIsAnIdempotentNoOpWhenDataAlreadyExists() {
    TestRigRepository rigs = mock(TestRigRepository.class);
    TestCycleRepository cycles = mock(TestCycleRepository.class);
    MeasurementRepository measurements = mock(MeasurementRepository.class);
    when(rigs.count()).thenReturn(3L);
    when(cycles.count()).thenReturn(1000L);
    when(measurements.count()).thenReturn(5000L);

    DemoDataService service =
        new DemoDataService(rigs, cycles, measurements, new ObjectMapper(), Path.of("missing"));

    DemoSeedResponse result = service.seed();

    assertThat(result.seeded()).isFalse();
    assertThat(result.rigCount()).isEqualTo(3);
    assertThat(result.cycleCount()).isEqualTo(1000);
    assertThat(result.syntheticNotice()).contains("Synthetic");
  }
}

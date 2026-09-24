package com.aerosense.aerosense.cycle;

import java.time.Instant;
import java.util.UUID;

public interface MeasurementTrendProjection {

  UUID getCycleId();

  String getCycleCode();

  Instant getRecordedAt();

  double getValue();
}

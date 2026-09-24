package com.aerosense.aerosense.demo;

import com.aerosense.aerosense.cycle.MeasurementEntity;
import com.aerosense.aerosense.cycle.MeasurementRepository;
import com.aerosense.aerosense.cycle.TestCycleEntity;
import com.aerosense.aerosense.cycle.TestCycleRepository;
import com.aerosense.aerosense.cycle.TestRigEntity;
import com.aerosense.aerosense.cycle.TestRigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemoDataService {

  private static final String SYNTHETIC_NOTICE =
      "Synthetic demonstration only. Not engineering or maintenance advice.";
  private static final Map<String, String> FEATURE_UNITS =
      Map.of(
          "extension_time_ms", "ms",
          "pressure_kpa", "kPa",
          "vibration_rms", "g_rms",
          "temperature_c", "degC",
          "cycle_duration_ms", "ms");

  private final TestRigRepository rigRepository;
  private final TestCycleRepository cycleRepository;
  private final MeasurementRepository measurementRepository;
  private final ObjectMapper objectMapper;
  private final Path demoDataDirectory;

  public DemoDataService(
      TestRigRepository rigRepository,
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository,
      ObjectMapper objectMapper,
      @Value("${aerosense.demo-data-directory:./data/generated}") Path demoDataDirectory) {
    this.rigRepository = rigRepository;
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
    this.objectMapper = objectMapper;
    this.demoDataDirectory = demoDataDirectory;
  }

  @Transactional
  public DemoSeedResponse seed() {
    long currentRigs = rigRepository.count();
    long currentCycles = cycleRepository.count();
    long currentMeasurements = measurementRepository.count();
    if (currentRigs > 0 && currentCycles > 0 && currentMeasurements > 0) {
      return new DemoSeedResponse(false, currentRigs, currentCycles, SYNTHETIC_NOTICE);
    }
    if (currentRigs != 0 || currentCycles != 0 || currentMeasurements != 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "The demo database is only partially seeded; reset it before reseeding.");
    }

    List<SyntheticRigInput> rigInputs = read("rigs.json", new TypeReference<>() {});
    List<SyntheticCycleInput> cycleInputs = read("cycles.json", new TypeReference<>() {});
    if (rigInputs.isEmpty() || cycleInputs.isEmpty()) {
      throw new IllegalStateException("The synthetic demo fixtures must contain rigs and cycles.");
    }

    Map<UUID, TestRigEntity> rigs = new HashMap<>();
    List<TestRigEntity> newRigs =
        rigInputs.stream()
            .map(
                input -> {
                  require(input.id() != null, "rig id is required");
                  require(input.rigCode() != null, "rigCode is required");
                  require(input.description() != null, "rig description is required");
                  require(
                      input.description().toLowerCase().contains("synthetic")
                          || input.description().toLowerCase().contains("fictional"),
                      "rig description must identify synthetic demo equipment");
                  TestRigEntity entity =
                      new TestRigEntity(input.id(), input.rigCode(), input.description());
                  require(rigs.put(input.id(), entity) == null, "duplicate rig id in fixture");
                  return entity;
                })
            .toList();
    List<TestRigEntity> savedRigs = rigRepository.saveAll(newRigs);
    rigs.clear();
    savedRigs.forEach(rig -> rigs.put(rig.getId(), rig));

    List<TestCycleEntity> newCycles = new ArrayList<>(cycleInputs.size());
    List<Map<String, Double>> featureVectors = new ArrayList<>(cycleInputs.size());
    for (SyntheticCycleInput input : cycleInputs) {
      require(input.id() != null, "cycle id is required");
      require(input.cycleCode() != null, "cycleCode is required");
      require(input.rigId() != null && rigs.containsKey(input.rigId()), "cycle rigId is unknown");
      require(
          input.cycleType() != null && input.cycleType().startsWith("synthetic-"),
          "cycleType must be synthetic");
      validateFeatures(input.features());
      TestCycleEntity entity =
          new TestCycleEntity(
              input.id(),
              input.cycleCode(),
              rigs.get(input.rigId()),
              Instant.parse(input.recordedAt()),
              input.cycleType(),
              input.syntheticLabel());
      newCycles.add(entity);
      featureVectors.add(input.features());
    }
    List<TestCycleEntity> savedCycles = cycleRepository.saveAll(newCycles);

    List<MeasurementEntity> measurements =
        new ArrayList<>(savedCycles.size() * FEATURE_UNITS.size());
    for (int index = 0; index < savedCycles.size(); index++) {
      TestCycleEntity cycle = savedCycles.get(index);
      for (Map.Entry<String, Double> feature : featureVectors.get(index).entrySet()) {
        measurements.add(
            new MeasurementEntity(
                cycle,
                feature.getKey(),
                feature.getValue(),
                FEATURE_UNITS.get(feature.getKey()),
                cycle.getRecordedAt()));
      }
    }
    measurementRepository.saveAll(measurements);
    return new DemoSeedResponse(true, newRigs.size(), savedCycles.size(), SYNTHETIC_NOTICE);
  }

  private <T> List<T> read(String fileName, TypeReference<List<T>> type) {
    try {
      return objectMapper.readValue(demoDataDirectory.resolve(fileName).toFile(), type);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not read synthetic fixture " + fileName + ".", exception);
    }
  }

  private void validateFeatures(Map<String, Double> features) {
    require(
        features != null && features.keySet().equals(Set.copyOf(FEATURE_UNITS.keySet())),
        "features must contain the five defined synthetic keys");
    for (Map.Entry<String, Double> feature : features.entrySet()) {
      require(
          feature.getValue() != null && Double.isFinite(feature.getValue()),
          "feature values must be finite numbers");
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException("Invalid synthetic demo fixture: " + message + ".");
    }
  }

  private record SyntheticRigInput(
      UUID id, String rigCode, String description, String syntheticNotice) {}

  private record SyntheticCycleInput(
      UUID id,
      String cycleCode,
      UUID rigId,
      String recordedAt,
      String cycleType,
      boolean syntheticLabel,
      Map<String, Double> features) {}
}

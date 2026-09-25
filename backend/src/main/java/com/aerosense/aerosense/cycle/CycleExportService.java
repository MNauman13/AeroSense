package com.aerosense.aerosense.cycle;

import com.aerosense.aerosense.analysis.AnomalyResultRepository;
import com.aerosense.aerosense.analysis.LatestCycleFlag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CycleExportService {

  private static final String SYNTHETIC_NOTICE =
      "Synthetic demonstration only. Not engineering or maintenance advice.";
  private static final List<String> FEATURES =
      List.of(
          "extension_time_ms",
          "pressure_kpa",
          "vibration_rms",
          "temperature_c",
          "cycle_duration_ms");

  private final TestRigRepository rigRepository;
  private final TestCycleRepository cycleRepository;
  private final MeasurementRepository measurementRepository;
  private final AnomalyResultRepository anomalyResultRepository;

  public CycleExportService(
      TestRigRepository rigRepository,
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository,
      AnomalyResultRepository anomalyResultRepository) {
    this.rigRepository = rigRepository;
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
    this.anomalyResultRepository = anomalyResultRepository;
  }

  public String createCsv(UUID rigId, Instant from, Instant to, Boolean flagged) {
    List<ExportRow> rows = findRows(rigId, from, to, flagged);
    List<String> header =
        new ArrayList<>(
            List.of(
                "synthetic_notice",
                "test_run_code",
                "recorded_at_utc",
                "test_bench_code",
                "test_type",
                "demo_comparison"));
    for (String feature : FEATURES) {
      header.add(feature);
      header.add(feature + "_unit");
    }

    StringBuilder csv = new StringBuilder();
    appendCsvRow(csv, header);
    for (ExportRow row : rows) {
      Map<String, MeasurementEntity> measurements =
          row.measurements().stream()
              .collect(
                  Collectors.toMap(MeasurementEntity::getFeatureName, measurement -> measurement));
      List<String> values =
          new ArrayList<>(
              List.of(
                  SYNTHETIC_NOTICE,
                  row.cycle().getCycleCode(),
                  row.cycle().getRecordedAt().toString(),
                  row.cycle().getRig().getRigCode(),
                  row.cycle().getCycleType(),
                  comparisonText(row.flagged())));
      for (String feature : FEATURES) {
        MeasurementEntity measurement = measurements.get(feature);
        values.add(measurement == null ? "" : Double.toString(measurement.getValue()));
        values.add(measurement == null ? "" : measurement.getUnit());
      }
      appendCsvRow(csv, values);
    }
    return csv.toString();
  }

  public String createReport(UUID rigId, Instant from, Instant to, Boolean flagged) {
    List<ExportRow> rows = findRows(rigId, from, to, flagged);
    long standsOut = rows.stream().filter(row -> Boolean.TRUE.equals(row.flagged())).count();
    long similar = rows.stream().filter(row -> Boolean.FALSE.equals(row.flagged())).count();
    long notCompared = rows.stream().filter(row -> row.flagged() == null).count();

    StringBuilder report = new StringBuilder("AeroSense synthetic test run report\n");
    report.append("Generated at (UTC): ").append(Instant.now()).append('\n');
    report.append("Test bench: ").append(rigLabel(rigId)).append('\n');
    report.append("From (inclusive, UTC): ").append(filterValue(from)).append('\n');
    report.append("To (inclusive, UTC): ").append(filterValue(to)).append('\n');
    report.append("Comparison filter: ").append(flaggedLabel(flagged)).append('\n');
    report.append("Matching test runs: ").append(rows.size()).append('\n');
    report.append("Stands out in demo comparison: ").append(standsOut).append('\n');
    report.append("Similar to others in demo comparison: ").append(similar).append('\n');
    report.append("Not compared: ").append(notCompared).append("\n\n");
    report.append("Reading summary across included runs\n");

    Map<String, List<MeasurementEntity>> byFeature = new HashMap<>();
    rows.stream()
        .flatMap(row -> row.measurements().stream())
        .forEach(
            measurement ->
                byFeature
                    .computeIfAbsent(measurement.getFeatureName(), ignored -> new ArrayList<>())
                    .add(measurement));
    for (String feature : FEATURES) {
      List<MeasurementEntity> values = byFeature.get(feature);
      if (values == null || values.isEmpty()) continue;
      double average = values.stream().mapToDouble(MeasurementEntity::getValue).average().orElse(0);
      double minimum = values.stream().mapToDouble(MeasurementEntity::getValue).min().orElse(0);
      double maximum = values.stream().mapToDouble(MeasurementEntity::getValue).max().orElse(0);
      report
          .append("- ")
          .append(feature)
          .append(" (")
          .append(values.getFirst().getUnit())
          .append("): ")
          .append(values.size())
          .append(" readings; average ")
          .append(format(average))
          .append(", range ")
          .append(format(minimum))
          .append(" to ")
          .append(format(maximum))
          .append('\n');
    }
    if (byFeature.isEmpty()) report.append("- No readings were recorded for these runs.\n");

    report
        .append('\n')
        .append(SYNTHETIC_NOTICE)
        .append(
            " Values and comparisons are fictional software output; they are not validated limits, a real equipment assessment, or safety guidance.\n");
    return report.toString();
  }

  private List<ExportRow> findRows(UUID rigId, Instant from, Instant to, Boolean flagged) {
    validateTimeRange(from, to);
    List<TestCycleEntity> cycles = cycleRepository.findFilteredForExport(rigId, from, to, flagged);
    if (cycles.isEmpty()) return List.of();

    List<UUID> cycleIds = cycles.stream().map(TestCycleEntity::getId).toList();
    Map<UUID, Boolean> latestFlags =
        anomalyResultRepository.findLatestFlagsByCycleIds(cycleIds).stream()
            .collect(Collectors.toMap(LatestCycleFlag::getCycleId, LatestCycleFlag::getIsFlagged));
    Map<UUID, List<MeasurementEntity>> measurementsByCycle = new HashMap<>();
    measurementRepository.findAllByCycle_IdInOrderByCycle_IdAscFeatureNameAsc(cycleIds).stream()
        .forEach(
            measurement ->
                measurementsByCycle
                    .computeIfAbsent(measurement.getCycle().getId(), ignored -> new ArrayList<>())
                    .add(measurement));

    return cycles.stream()
        .map(
            cycle ->
                new ExportRow(
                    cycle,
                    latestFlags.get(cycle.getId()),
                    measurementsByCycle.getOrDefault(cycle.getId(), List.of())))
        .toList();
  }

  private void appendCsvRow(StringBuilder csv, List<String> values) {
    csv.append(values.stream().map(this::escapeCsv).collect(Collectors.joining(",")))
        .append("\r\n");
  }

  private String escapeCsv(String value) {
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("\n")
        || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private String comparisonText(Boolean flagged) {
    if (flagged == null) return "Not compared";
    return flagged ? "Stands out in demo comparison" : "Similar to others in demo comparison";
  }

  private String rigLabel(UUID rigId) {
    if (rigId == null) return "All fictional test benches";
    return rigRepository
        .findById(rigId)
        .map(TestRigEntity::getRigCode)
        .orElse("Unknown test bench");
  }

  private String filterValue(Instant value) {
    return value == null ? "Any" : value.toString();
  }

  private String flaggedLabel(Boolean flagged) {
    if (flagged == null) return "Any comparison status";
    return flagged ? "Stands out only" : "Similar to others only";
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private void validateTimeRange(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must be earlier than or equal to to");
    }
  }

  private record ExportRow(
      TestCycleEntity cycle, Boolean flagged, List<MeasurementEntity> measurements) {}
}

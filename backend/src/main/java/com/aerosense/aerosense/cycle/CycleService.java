package com.aerosense.aerosense.cycle;

import com.aerosense.aerosense.analysis.AnomalyResultRepository;
import com.aerosense.aerosense.analysis.LatestCycleFlag;
import com.aerosense.aerosense.common.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CycleService {

  private static final String SYNTHETIC_RIG_NOTICE =
      "Synthetic demonstration rig. Not real aircraft equipment.";

  private final TestRigRepository rigRepository;
  private final TestCycleRepository cycleRepository;
  private final MeasurementRepository measurementRepository;
  private final AnomalyResultRepository anomalyResultRepository;

  public CycleService(
      TestRigRepository rigRepository,
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository,
      AnomalyResultRepository anomalyResultRepository) {
    this.rigRepository = rigRepository;
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
    this.anomalyResultRepository = anomalyResultRepository;
  }

  public List<RigResponse> listRigs() {
    return rigRepository.findAllByOrderByRigCodeAsc().stream()
        .map(
            rig ->
                new RigResponse(
                    rig.getId(), rig.getRigCode(), rig.getDescription(), SYNTHETIC_RIG_NOTICE))
        .toList();
  }

  public PageResponse<CycleResponse> listCycles(
      UUID rigId, Instant from, Instant to, Boolean flagged, int page, int size) {
    validatePage(page, size);
    validateTimeRange(from, to);
    var pageable = PageRequest.of(page, size);

    Page<TestCycleEntity> cycles = cycleRepository.findFiltered(rigId, from, to, flagged, pageable);
    List<UUID> cycleIds = cycles.getContent().stream().map(TestCycleEntity::getId).toList();
    Map<UUID, Boolean> latestFlags =
        cycleIds.isEmpty()
            ? Map.of()
            : anomalyResultRepository.findLatestFlagsByCycleIds(cycleIds).stream()
                .collect(
                    Collectors.toMap(LatestCycleFlag::getCycleId, LatestCycleFlag::getIsFlagged));
    List<CycleResponse> items =
        cycles.getContent().stream()
            .map(cycle -> toResponse(cycle, latestFlags.get(cycle.getId())))
            .toList();
    return new PageResponse<>(items, page, size, cycles.getTotalElements());
  }

  public CycleDetailResponse getCycle(UUID cycleId) {
    TestCycleEntity cycle =
        cycleRepository
            .findById(cycleId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Synthetic cycle not found."));
    List<MeasurementResponse> measurements =
        measurementRepository.findAllByCycle_IdOrderByFeatureNameAsc(cycleId).stream()
            .map(
                measurement ->
                    new MeasurementResponse(
                        measurement.getFeatureName(),
                        measurement.getValue(),
                        measurement.getUnit(),
                        measurement.getMeasuredAt()))
            .toList();
    Boolean latestFlag =
        anomalyResultRepository
            .findFirstByTestCycle_IdOrderByCreatedAtDesc(cycleId)
            .map(result -> result.isFlagged())
            .orElse(null);
    return new CycleDetailResponse(
        cycle.getId(),
        cycle.getCycleCode(),
        cycle.getRig().getId(),
        cycle.getRecordedAt(),
        cycle.getCycleType(),
        latestFlag,
        measurements);
  }

  private CycleResponse toResponse(TestCycleEntity cycle) {
    Boolean latestFlag =
        anomalyResultRepository
            .findFirstByTestCycle_IdOrderByCreatedAtDesc(cycle.getId())
            .map(result -> result.isFlagged())
            .orElse(null);
    return toResponse(cycle, latestFlag);
  }

  private CycleResponse toResponse(TestCycleEntity cycle, Boolean latestFlag) {
    return new CycleResponse(
        cycle.getId(),
        cycle.getCycleCode(),
        cycle.getRig().getId(),
        cycle.getRecordedAt(),
        cycle.getCycleType(),
        latestFlag);
  }

  private void validatePage(int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be at least 0");
    }
    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("size must be between 1 and 100");
    }
  }

  private void validateTimeRange(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must be earlier than or equal to to");
    }
  }
}

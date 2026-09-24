package com.aerosense.aerosense.cycle;

import com.aerosense.aerosense.common.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  public CycleService(
      TestRigRepository rigRepository,
      TestCycleRepository cycleRepository,
      MeasurementRepository measurementRepository) {
    this.rigRepository = rigRepository;
    this.cycleRepository = cycleRepository;
    this.measurementRepository = measurementRepository;
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

    // Until Phase 7 creates analysis results, the flag state is unknown rather than false.
    if (flagged != null) {
      return new PageResponse<>(List.of(), page, size, 0);
    }

    Page<TestCycleEntity> cycles = cycleRepository.findFiltered(rigId, from, to, pageable);
    List<CycleResponse> items = cycles.getContent().stream().map(this::toResponse).toList();
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
    return new CycleDetailResponse(
        cycle.getId(),
        cycle.getCycleCode(),
        cycle.getRig().getId(),
        cycle.getRecordedAt(),
        cycle.getCycleType(),
        null,
        measurements);
  }

  private CycleResponse toResponse(TestCycleEntity cycle) {
    return new CycleResponse(
        cycle.getId(),
        cycle.getCycleCode(),
        cycle.getRig().getId(),
        cycle.getRecordedAt(),
        cycle.getCycleType(),
        null);
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

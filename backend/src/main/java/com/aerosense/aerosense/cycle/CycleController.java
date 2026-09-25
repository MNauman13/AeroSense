package com.aerosense.aerosense.cycle;

import com.aerosense.aerosense.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class CycleController {

  private final CycleService cycleService;
  private final CycleExportService cycleExportService;

  public CycleController(CycleService cycleService, CycleExportService cycleExportService) {
    this.cycleService = cycleService;
    this.cycleExportService = cycleExportService;
  }

  @GetMapping("/rigs")
  public List<RigResponse> listRigs() {
    return cycleService.listRigs();
  }

  @GetMapping("/cycles")
  public PageResponse<CycleResponse> listCycles(
      @RequestParam(required = false) UUID rigId,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to,
      @RequestParam(required = false) Boolean flagged,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return cycleService.listCycles(rigId, from, to, flagged, page, size);
  }

  @GetMapping("/cycles/export")
  public ResponseEntity<String> exportCycles(
      @RequestParam(required = false) UUID rigId,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to,
      @RequestParam(required = false) Boolean flagged) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("aerosense-synthetic-test-runs.csv")
                .build()
                .toString())
        .body(cycleExportService.createCsv(rigId, from, to, flagged));
  }

  @GetMapping("/cycles/report")
  public ResponseEntity<String> downloadCycleReport(
      @RequestParam(required = false) UUID rigId,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to,
      @RequestParam(required = false) Boolean flagged) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("aerosense-synthetic-test-report.txt")
                .build()
                .toString())
        .body(cycleExportService.createReport(rigId, from, to, flagged));
  }

  @GetMapping("/cycles/{cycleId}")
  public CycleDetailResponse getCycle(@PathVariable UUID cycleId) {
    return cycleService.getCycle(cycleId);
  }
}

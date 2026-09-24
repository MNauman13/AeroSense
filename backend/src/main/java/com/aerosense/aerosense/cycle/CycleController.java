package com.aerosense.aerosense.cycle;

import com.aerosense.aerosense.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  public CycleController(CycleService cycleService) {
    this.cycleService = cycleService;
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

  @GetMapping("/cycles/{cycleId}")
  public CycleDetailResponse getCycle(@PathVariable UUID cycleId) {
    return cycleService.getCycle(cycleId);
  }
}

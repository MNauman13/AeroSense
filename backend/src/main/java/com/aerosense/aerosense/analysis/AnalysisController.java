package com.aerosense.aerosense.analysis;

import com.aerosense.aerosense.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class AnalysisController {

  private final AnalysisRunService analysisRunService;

  public AnalysisController(AnalysisRunService analysisRunService) {
    this.analysisRunService = analysisRunService;
  }

  @PostMapping("/analysis-runs")
  public AnalysisRunResponse createRun(@Valid @RequestBody AnalysisRunRequest request) {
    return analysisRunService.create(request);
  }

  @GetMapping("/analysis-runs/{runId}")
  public AnalysisRunResponse getRun(@PathVariable UUID runId) {
    return analysisRunService.getRun(runId);
  }

  @GetMapping("/analysis-runs/{runId}/results")
  public PageResponse<AnalysisResultResponse> getResults(
      @PathVariable UUID runId,
      @RequestParam(required = false) Boolean flagged,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return analysisRunService.getResults(runId, flagged, page, size);
  }

  @GetMapping("/analysis-runs/{runId}/evaluation")
  public EvaluationResponse evaluate(@PathVariable UUID runId) {
    return analysisRunService.evaluate(runId);
  }

  @GetMapping("/cycles/{cycleId}/analysis/latest")
  public AnalysisResultResponse latestForCycle(@PathVariable UUID cycleId) {
    return analysisRunService.getLatestForCycle(cycleId);
  }

  @GetMapping("/metrics/summary")
  public MetricsSummaryResponse summary(
      @RequestParam(required = false) UUID rigId,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to) {
    return analysisRunService.getSummary(rigId, from, to);
  }
}

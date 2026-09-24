package com.aerosense.aerosense.analysis;

import com.aerosense.aerosense.cycle.TestCycleEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "anomaly_result",
    uniqueConstraints = @UniqueConstraint(columnNames = {"analysis_run_id", "cycle_id"}))
public class AnomalyResultEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_run_id", nullable = false)
  private AnalysisRunEntity analysisRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private TestCycleEntity testCycle;

  @Column(nullable = false)
  private double score;

  @Column(nullable = false)
  private double threshold;

  @Column(name = "is_flagged", nullable = false)
  private boolean isFlagged;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "explanation_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> explanationJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AnomalyResultEntity() {}

  public AnomalyResultEntity(
      UUID id,
      AnalysisRunEntity analysisRun,
      TestCycleEntity testCycle,
      double score,
      double threshold,
      boolean isFlagged,
      Map<String, Object> explanationJson,
      Instant createdAt) {
    this.id = id;
    this.analysisRun = analysisRun;
    this.testCycle = testCycle;
    this.score = score;
    this.threshold = threshold;
    this.isFlagged = isFlagged;
    this.explanationJson = explanationJson;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public AnalysisRunEntity getAnalysisRun() {
    return analysisRun;
  }

  public TestCycleEntity getTestCycle() {
    return testCycle;
  }

  public double getScore() {
    return score;
  }

  public double getThreshold() {
    return threshold;
  }

  public boolean isFlagged() {
    return isFlagged;
  }

  public Map<String, Object> getExplanationJson() {
    return explanationJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

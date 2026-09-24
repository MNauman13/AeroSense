package com.aerosense.aerosense.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_run")
public class AnalysisRunEntity {

  @Id private UUID id;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "model_name", nullable = false, length = 80)
  private String modelName;

  @Column(name = "model_version", nullable = false, length = 40)
  private String modelVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> configJson;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "error_message")
  private String errorMessage;

  protected AnalysisRunEntity() {}

  public AnalysisRunEntity(
      UUID id,
      Instant startedAt,
      String modelName,
      String modelVersion,
      Map<String, Object> configJson,
      String status) {
    this.id = id;
    this.startedAt = startedAt;
    this.modelName = modelName;
    this.modelVersion = modelVersion;
    this.configJson = configJson;
    this.status = status;
  }

  public void succeed(Instant completedAt) {
    this.completedAt = completedAt;
    this.status = "SUCCEEDED";
    this.errorMessage = null;
  }

  public void setModelVersion(String modelName, String modelVersion) {
    this.modelName = modelName;
    this.modelVersion = modelVersion;
  }

  public void fail(Instant completedAt, String safeMessage) {
    this.completedAt = completedAt;
    this.status = "FAILED";
    this.errorMessage = safeMessage;
  }

  public UUID getId() {
    return id;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getModelName() {
    return modelName;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public Map<String, Object> getConfigJson() {
    return configJson;
  }

  public String getStatus() {
    return status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}

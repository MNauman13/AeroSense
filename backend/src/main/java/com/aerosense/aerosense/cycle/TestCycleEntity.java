package com.aerosense.aerosense.cycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_cycle")
public class TestCycleEntity {

  @Id private UUID id;

  @Column(name = "cycle_code", nullable = false, unique = true, length = 60)
  private String cycleCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rig_id", nullable = false)
  private TestRigEntity rig;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "cycle_type", nullable = false, length = 40)
  private String cycleType;

  @Column(name = "synthetic_label", nullable = false)
  private boolean syntheticLabel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TestCycleEntity() {}

  public TestCycleEntity(
      UUID id,
      String cycleCode,
      TestRigEntity rig,
      Instant recordedAt,
      String cycleType,
      boolean syntheticLabel) {
    this.id = id;
    this.cycleCode = cycleCode;
    this.rig = rig;
    this.recordedAt = recordedAt;
    this.cycleType = cycleType;
    this.syntheticLabel = syntheticLabel;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCycleCode() {
    return cycleCode;
  }

  public TestRigEntity getRig() {
    return rig;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public String getCycleType() {
    return cycleType;
  }

  public boolean isSyntheticLabel() {
    return syntheticLabel;
  }
}

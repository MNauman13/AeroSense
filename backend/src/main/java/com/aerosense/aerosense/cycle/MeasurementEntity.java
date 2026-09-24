package com.aerosense.aerosense.cycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "measurement",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "feature_name"}))
public class MeasurementEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private TestCycleEntity cycle;

  @Column(name = "feature_name", nullable = false, length = 60)
  private String featureName;

  @Column(name = "\"value\"", nullable = false)
  private double value;

  @Column(nullable = false, length = 24)
  private String unit;

  @Column(name = "measured_at", nullable = false)
  private Instant measuredAt;

  protected MeasurementEntity() {}

  public MeasurementEntity(
      TestCycleEntity cycle, String featureName, double value, String unit, Instant measuredAt) {
    this.cycle = cycle;
    this.featureName = featureName;
    this.value = value;
    this.unit = unit;
    this.measuredAt = measuredAt;
  }

  public Long getId() {
    return id;
  }

  public TestCycleEntity getCycle() {
    return cycle;
  }

  public String getFeatureName() {
    return featureName;
  }

  public double getValue() {
    return value;
  }

  public String getUnit() {
    return unit;
  }

  public Instant getMeasuredAt() {
    return measuredAt;
  }
}

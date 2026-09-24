package com.aerosense.aerosense.cycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_rig")
public class TestRigEntity {

  @Id private UUID id;

  @Column(name = "rig_code", nullable = false, unique = true, length = 40)
  private String rigCode;

  @Column(nullable = false)
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TestRigEntity() {}

  public TestRigEntity(UUID id, String rigCode, String description) {
    this.id = id;
    this.rigCode = rigCode;
    this.description = description;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRigCode() {
    return rigCode;
  }

  public String getDescription() {
    return description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

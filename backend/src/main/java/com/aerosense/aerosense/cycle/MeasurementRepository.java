package com.aerosense.aerosense.cycle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {

  List<MeasurementEntity> findAllByCycle_IdOrderByFeatureNameAsc(UUID cycleId);
}

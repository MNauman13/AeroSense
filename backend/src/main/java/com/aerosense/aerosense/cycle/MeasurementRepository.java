package com.aerosense.aerosense.cycle;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {

  List<MeasurementEntity> findAllByCycle_IdOrderByFeatureNameAsc(UUID cycleId);

  List<MeasurementEntity> findAllByCycle_IdInOrderByCycle_IdAscFeatureNameAsc(
      Collection<UUID> cycleIds);

  @Query(
      """
      select m.featureName as featureName, m.unit as unit, count(m.id) as sampleCount,
        avg(m.value) as average, min(m.value) as minimum, max(m.value) as maximum
      from MeasurementEntity m group by m.featureName, m.unit order by m.featureName
      """)
  List<MeasurementAggregate> summarizeByFeature();
}

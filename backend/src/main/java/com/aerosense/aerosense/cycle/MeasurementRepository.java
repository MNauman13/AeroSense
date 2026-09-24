package com.aerosense.aerosense.cycle;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {

  List<MeasurementEntity> findAllByCycle_IdOrderByFeatureNameAsc(UUID cycleId);

  List<MeasurementEntity> findAllByCycle_IdInOrderByCycle_IdAscFeatureNameAsc(
      Collection<UUID> cycleIds);

  @Query(
      """
      select m.featureName as featureName, m.unit as unit, count(m.id) as sampleCount,
        avg(m.value) as average, min(m.value) as minimum, max(m.value) as maximum
      from MeasurementEntity m
      where (:rigId is null or m.cycle.rig.id = :rigId)
        and (:fromTime is null or m.cycle.recordedAt >= :fromTime)
        and (:toTime is null or m.cycle.recordedAt <= :toTime)
      group by m.featureName, m.unit order by m.featureName
      """)
  List<MeasurementAggregate> summarizeByFeature(
      @Param("rigId") UUID rigId,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime);
}

package com.aerosense.aerosense.cycle;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCycleRepository extends JpaRepository<TestCycleEntity, UUID> {

  @Query(
      """
      select c from TestCycleEntity c
      where (:rigId is null or c.rig.id = :rigId)
        and (:fromTime is null or c.recordedAt >= :fromTime)
        and (:toTime is null or c.recordedAt <= :toTime)
      order by c.recordedAt desc, c.cycleCode asc
      """)
  Page<TestCycleEntity> findFiltered(
      @Param("rigId") UUID rigId,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime,
      Pageable pageable);
}

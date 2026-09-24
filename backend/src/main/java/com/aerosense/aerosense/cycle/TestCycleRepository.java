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
        and (:flagged is null or exists (
          select result.id from AnomalyResultEntity result
          where result.testCycle = c and result.isFlagged = :flagged
            and result.createdAt = (
              select max(latest.createdAt) from AnomalyResultEntity latest
              where latest.testCycle = c
            )
        ))
      order by c.recordedAt desc, c.cycleCode asc
      """)
  Page<TestCycleEntity> findFiltered(
      @Param("rigId") UUID rigId,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime,
      @Param("flagged") Boolean flagged,
      Pageable pageable);
}

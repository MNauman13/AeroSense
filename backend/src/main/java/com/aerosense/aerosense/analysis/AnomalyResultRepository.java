package com.aerosense.aerosense.analysis;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyResultRepository extends JpaRepository<AnomalyResultEntity, UUID> {

  Page<AnomalyResultEntity> findByAnalysisRun_IdAndIsFlagged(
      UUID analysisRunId, boolean isFlagged, Pageable pageable);

  Page<AnomalyResultEntity> findByAnalysisRun_Id(UUID analysisRunId, Pageable pageable);

  List<AnomalyResultEntity> findAllByAnalysisRun_Id(UUID analysisRunId);

  long countByAnalysisRun_Id(UUID analysisRunId);

  Optional<AnomalyResultEntity> findFirstByTestCycle_IdOrderByCreatedAtDesc(UUID cycleId);

  @Query(
      """
      select result.testCycle.id as cycleId, result.isFlagged as isFlagged
      from AnomalyResultEntity result
      where result.testCycle.id in :cycleIds
        and result.createdAt = (
          select max(latest.createdAt) from AnomalyResultEntity latest
          where latest.testCycle.id = result.testCycle.id
        )
      """)
  List<LatestCycleFlag> findLatestFlagsByCycleIds(@Param("cycleIds") Collection<UUID> cycleIds);

  @Query(
      """
      select count(distinct result.testCycle.id) from AnomalyResultEntity result
      where result.analysisRun.status = 'SUCCEEDED'
        and (:rigId is null or result.testCycle.rig.id = :rigId)
        and (:fromTime is null or result.testCycle.recordedAt >= :fromTime)
        and (:toTime is null or result.testCycle.recordedAt <= :toTime)
      """)
  long countAnalyzedCycles(
      @Param("rigId") UUID rigId,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime);

  @Query(
      """
      select count(distinct result.testCycle.id) from AnomalyResultEntity result
      where result.isFlagged = true
        and (:rigId is null or result.testCycle.rig.id = :rigId)
        and (:fromTime is null or result.testCycle.recordedAt >= :fromTime)
        and (:toTime is null or result.testCycle.recordedAt <= :toTime)
        and result.createdAt = (
        select max(latest.createdAt) from AnomalyResultEntity latest
        where latest.testCycle.id = result.testCycle.id
      )
      """)
  long countLatestFlaggedCycles(
      @Param("rigId") UUID rigId,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime);
}

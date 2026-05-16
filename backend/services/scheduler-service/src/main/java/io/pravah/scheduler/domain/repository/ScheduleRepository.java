package io.pravah.scheduler.domain.repository;

import io.pravah.scheduler.domain.model.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

  List<Schedule> findByTenantIdAndPipelineIdOrderByCreatedAtDesc(UUID tenantId, UUID pipelineId);

  @Query(
      value =
          """
          SELECT * FROM schedules
          WHERE is_active = true
            AND next_run_at IS NOT NULL
            AND next_run_at <= :now
          ORDER BY next_run_at
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<Schedule> findDueSchedulesForUpdate(@Param("now") Instant now);

  Optional<Schedule> findByIdAndTenantId(UUID id, UUID tenantId);
}

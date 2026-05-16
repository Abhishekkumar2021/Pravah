package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.JobLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLogEntityRepository extends JpaRepository<JobLogEntity, UUID> {

  @Query(
      """
      SELECT l FROM JobLogEntity l
      WHERE l.jobId = :jobId
        AND (:level IS NULL OR l.level = :level)
      ORDER BY l.logTime ASC
      """)
  List<JobLogEntity> findByJobIdAndOptionalLevel(
      @Param("jobId") UUID jobId, @Param("level") JobLogLevel level);
}

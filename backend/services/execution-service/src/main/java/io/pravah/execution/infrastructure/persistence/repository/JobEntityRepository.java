package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEntityRepository extends JpaRepository<JobEntity, UUID> {

  List<JobEntity> findByExecutionIdOrderByStageIdAsc(UUID executionId);

  List<JobEntity> findByStatus(JobState status);

  /**
   * Finds a job by execution ID and stage ID (for stage output resolution).
   *
   * @param executionId the execution ID
   * @param stageId the stage ID
   * @return the job if found
   */
  Optional<JobEntity> findByExecutionIdAndStageId(UUID executionId, String stageId);
}

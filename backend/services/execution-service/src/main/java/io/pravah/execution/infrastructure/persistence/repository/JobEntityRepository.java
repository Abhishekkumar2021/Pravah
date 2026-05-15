package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEntityRepository extends JpaRepository<JobEntity, UUID> {

  List<JobEntity> findByExecutionIdOrderByStageIdAsc(UUID executionId);
}

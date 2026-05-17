package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.CheckpointEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface CheckpointRepository
    extends JpaRepository<CheckpointEntity, CheckpointEntity.CheckpointId> {

  List<CheckpointEntity> findByExecutionId(UUID executionId);

  Optional<CheckpointEntity> findByExecutionIdAndStageId(UUID executionId, String stageId);

  @Modifying
  void deleteByExecutionId(UUID executionId);
}

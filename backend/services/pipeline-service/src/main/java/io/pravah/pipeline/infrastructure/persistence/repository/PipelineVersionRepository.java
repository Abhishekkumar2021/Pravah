package io.pravah.pipeline.infrastructure.persistence.repository;

import io.pravah.pipeline.infrastructure.persistence.entity.PipelineVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineVersionRepository extends JpaRepository<PipelineVersionEntity, UUID> {

  List<PipelineVersionEntity> findByPipelineIdOrderByVersionDesc(UUID pipelineId);

  Optional<PipelineVersionEntity> findByPipelineIdAndVersion(UUID pipelineId, int version);

  Optional<PipelineVersionEntity> findFirstByPipelineIdOrderByVersionDesc(UUID pipelineId);

  int countByPipelineId(UUID pipelineId);

  void deleteByPipelineIdAndVersion(UUID pipelineId, int version);
}

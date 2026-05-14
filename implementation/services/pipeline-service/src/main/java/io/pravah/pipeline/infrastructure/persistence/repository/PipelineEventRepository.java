package io.pravah.pipeline.infrastructure.persistence.repository;

import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineEventRepository extends JpaRepository<PipelineEventEntity, UUID> {

  int countByPipelineId(UUID pipelineId);

  List<PipelineEventEntity> findByPipelineIdOrderByEventVersionAsc(UUID pipelineId);
}

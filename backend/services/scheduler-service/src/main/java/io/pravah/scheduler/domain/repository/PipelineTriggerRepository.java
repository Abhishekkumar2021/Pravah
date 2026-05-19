package io.pravah.scheduler.domain.repository;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineTriggerRepository extends JpaRepository<PipelineTrigger, UUID> {

  List<PipelineTrigger> findByTenantIdAndPipelineIdOrderByCreatedAtDesc(
      UUID tenantId, UUID pipelineId);

  Optional<PipelineTrigger> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndPipelineIdAndName(UUID tenantId, UUID pipelineId, String name);

  List<PipelineTrigger> findByTriggerTypeAndEnabledTrue(String triggerType);
}

package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, UUID> {

  List<AlertRuleEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<AlertRuleEntity> findByTenantIdAndPipelineIdOrderByCreatedAtDesc(
      UUID tenantId, UUID pipelineId);

  Optional<AlertRuleEntity> findByTenantIdAndName(UUID tenantId, String name);

  /** Find enabled rules for a tenant, optionally filtered by pipeline (or pipeline is null). */
  @Query(
      """
      SELECT r FROM AlertRuleEntity r
      WHERE r.tenantId = :tenantId
        AND r.enabled = true
        AND (r.pipelineId IS NULL OR r.pipelineId = :pipelineId)
      ORDER BY r.pipelineId NULLS LAST
      """)
  List<AlertRuleEntity> findEnabledRulesForPipeline(
      @Param("tenantId") UUID tenantId, @Param("pipelineId") UUID pipelineId);

  /** Find tenant-wide enabled rules (where pipeline_id IS NULL). */
  @Query(
      """
      SELECT r FROM AlertRuleEntity r
      WHERE r.tenantId = :tenantId
        AND r.enabled = true
        AND r.pipelineId IS NULL
      """)
  List<AlertRuleEntity> findEnabledTenantWideRules(@Param("tenantId") UUID tenantId);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}

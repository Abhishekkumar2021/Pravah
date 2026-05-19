package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

  Page<AuditLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  @Query(
      """
      SELECT a FROM AuditLogEntity a
      WHERE a.tenantId = :tenantId
        AND (:action IS NULL OR a.action = :action)
        AND (:resourceType IS NULL OR a.resourceType = :resourceType)
        AND (:resourceId IS NULL OR a.resourceId = :resourceId)
        AND (:actorId IS NULL OR a.actorId = :actorId)
        AND (:from IS NULL OR a.createdAt >= :from)
        AND (:to IS NULL OR a.createdAt <= :to)
      ORDER BY a.createdAt DESC
      """)
  Page<AuditLogEntity> findFiltered(
      @Param("tenantId") UUID tenantId,
      @Param("action") String action,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("actorId") UUID actorId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  Page<AuditLogEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
      UUID tenantId, String resourceType, UUID resourceId, Pageable pageable);
}

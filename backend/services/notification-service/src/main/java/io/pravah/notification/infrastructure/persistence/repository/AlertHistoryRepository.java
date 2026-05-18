package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.AlertHistoryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistoryEntity, UUID> {

  Page<AlertHistoryEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  Page<AlertHistoryEntity> findByTenantIdAndAlertRuleIdOrderByCreatedAtDesc(
      UUID tenantId, UUID alertRuleId, Pageable pageable);

  /** Check for recent duplicate alert within dedup window. */
  @Query(
      """
      SELECT COUNT(h) > 0 FROM AlertHistoryEntity h
      WHERE h.tenantId = :tenantId
        AND h.alertRuleId = :alertRuleId
        AND h.dedupKey = :dedupKey
        AND h.createdAt > :windowStart
      """)
  boolean existsRecentDuplicate(
      @Param("tenantId") UUID tenantId,
      @Param("alertRuleId") UUID alertRuleId,
      @Param("dedupKey") String dedupKey,
      @Param("windowStart") Instant windowStart);

  List<AlertHistoryEntity> findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
      UUID tenantId, Instant after);
}

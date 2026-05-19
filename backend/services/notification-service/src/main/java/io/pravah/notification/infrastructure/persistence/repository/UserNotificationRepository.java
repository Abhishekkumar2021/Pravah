package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.UserNotificationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, UUID> {

  Page<UserNotificationEntity> findByTenantIdAndUserIdOrderByCreatedAtDesc(
      UUID tenantId, UUID userId, Pageable pageable);

  List<UserNotificationEntity> findByTenantIdAndUserIdAndReadFalseOrderByCreatedAtDesc(
      UUID tenantId, UUID userId);

  long countByTenantIdAndUserIdAndReadFalse(UUID tenantId, UUID userId);

  @Modifying
  @Query(
      """
      UPDATE UserNotificationEntity n
      SET n.read = true, n.readAt = CURRENT_TIMESTAMP
      WHERE n.tenantId = :tenantId AND n.userId = :userId AND n.read = false
      """)
  int markAllAsRead(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Modifying
  @Query(
      """
      DELETE FROM UserNotificationEntity n
      WHERE n.tenantId = :tenantId AND n.expiresAt IS NOT NULL AND n.expiresAt < CURRENT_TIMESTAMP
      """)
  int deleteExpired(@Param("tenantId") UUID tenantId);
}

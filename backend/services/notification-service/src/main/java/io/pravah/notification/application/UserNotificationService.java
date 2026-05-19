package io.pravah.notification.application;

import io.pravah.notification.api.dto.UserNotificationResponse;
import io.pravah.notification.domain.NotificationType;
import io.pravah.notification.infrastructure.persistence.entity.UserNotificationEntity;
import io.pravah.notification.infrastructure.persistence.repository.UserNotificationRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing user in-app notifications (notification center). */
@Service
public class UserNotificationService {

  private final UserNotificationRepository notificationRepository;

  public UserNotificationService(UserNotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  private UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }

  private UUID requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No user context set");
    }
    return userId;
  }

  /** Create a notification for a user (called by alert dispatch or other services). */
  @Transactional
  public void createNotification(
      UUID tenantId,
      UUID userId,
      String title,
      String message,
      NotificationType type,
      String resourceType,
      UUID resourceId,
      String linkUrl,
      UUID alertHistoryId) {

    TenantContext.setCurrentTenantId(tenantId);
    try {
      UserNotificationEntity notification =
          new UserNotificationEntity(
              tenantId,
              userId,
              title,
              message,
              type,
              resourceType,
              resourceId,
              linkUrl,
              alertHistoryId);

      notificationRepository.save(notification);
    } finally {
      TenantContext.clear();
    }
  }

  /** Get paginated notifications for the current user. */
  @Transactional(readOnly = true)
  public Page<UserNotificationResponse> getNotifications(int page, int size) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    return notificationRepository
        .findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, pageable)
        .map(UserNotificationResponse::from);
  }

  /** Get unread notifications for the current user. */
  @Transactional(readOnly = true)
  public List<UserNotificationResponse> getUnreadNotifications() {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    return notificationRepository
        .findByTenantIdAndUserIdAndReadFalseOrderByCreatedAtDesc(tenantId, userId)
        .stream()
        .map(UserNotificationResponse::from)
        .toList();
  }

  /** Get unread count for the current user (for badge). */
  @Transactional(readOnly = true)
  public long getUnreadCount() {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    return notificationRepository.countByTenantIdAndUserIdAndReadFalse(tenantId, userId);
  }

  /** Mark a notification as read. */
  @Transactional
  public void markAsRead(UUID notificationId) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    notificationRepository
        .findById(notificationId)
        .filter(n -> n.getTenantId().equals(tenantId) && n.getUserId().equals(userId))
        .ifPresent(UserNotificationEntity::markAsRead);
  }

  /** Mark all notifications as read for the current user. */
  @Transactional
  public int markAllAsRead() {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    return notificationRepository.markAllAsRead(tenantId, userId);
  }
}

package io.pravah.notification.infrastructure.persistence.entity;

import io.pravah.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** User notification entity for in-app notification center (bell icon). */
@Entity
@Table(name = "user_notifications")
public class UserNotificationEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String title;

  private String message;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private NotificationType type;

  @Column(name = "resource_type")
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "link_url")
  private String linkUrl;

  @Column(nullable = false)
  private boolean read = false;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "alert_history_id")
  private UUID alertHistoryId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected UserNotificationEntity() {}

  public UserNotificationEntity(
      UUID tenantId,
      UUID userId,
      String title,
      String message,
      NotificationType type,
      String resourceType,
      UUID resourceId,
      String linkUrl,
      UUID alertHistoryId) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.userId = userId;
    this.title = title;
    this.message = message;
    this.type = type;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.linkUrl = linkUrl;
    this.alertHistoryId = alertHistoryId;
    this.createdAt = Instant.now();
    this.read = false;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public NotificationType getType() {
    return type;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getLinkUrl() {
    return linkUrl;
  }

  public boolean isRead() {
    return read;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public UUID getAlertHistoryId() {
    return alertHistoryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void markAsRead() {
    this.read = true;
    this.readAt = Instant.now();
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}

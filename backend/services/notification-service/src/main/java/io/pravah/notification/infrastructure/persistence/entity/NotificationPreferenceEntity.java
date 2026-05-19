package io.pravah.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferenceEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled = true;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled = true;

  @Column(name = "event_preferences", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String eventPreferences = "{}";

  @Column(name = "quiet_hours_start")
  private LocalTime quietHoursStart;

  @Column(name = "quiet_hours_end")
  private LocalTime quietHoursEnd;

  @Column(name = "quiet_hours_tz")
  private String quietHoursTz;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NotificationPreferenceEntity() {}

  public NotificationPreferenceEntity(UUID tenantId, UUID userId) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.userId = userId;
    this.updatedAt = Instant.now();
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

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public boolean isInAppEnabled() {
    return inAppEnabled;
  }

  public String getEventPreferences() {
    return eventPreferences;
  }

  public LocalTime getQuietHoursStart() {
    return quietHoursStart;
  }

  public LocalTime getQuietHoursEnd() {
    return quietHoursEnd;
  }

  public String getQuietHoursTz() {
    return quietHoursTz;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(
      boolean emailEnabled,
      boolean inAppEnabled,
      String eventPreferences,
      LocalTime quietHoursStart,
      LocalTime quietHoursEnd,
      String quietHoursTz) {
    this.emailEnabled = emailEnabled;
    this.inAppEnabled = inAppEnabled;
    this.eventPreferences = eventPreferences != null ? eventPreferences : "{}";
    this.quietHoursStart = quietHoursStart;
    this.quietHoursEnd = quietHoursEnd;
    this.quietHoursTz = quietHoursTz;
    this.updatedAt = Instant.now();
  }
}

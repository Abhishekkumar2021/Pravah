package io.pravah.notification.api.dto;

import io.pravah.notification.infrastructure.persistence.entity.NotificationPreferenceEntity;
import java.time.Instant;
import java.time.LocalTime;

public record NotificationPreferenceResponse(
    boolean emailEnabled,
    boolean inAppEnabled,
    String eventPreferences,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd,
    String quietHoursTz,
    Instant updatedAt) {

  public static NotificationPreferenceResponse from(NotificationPreferenceEntity entity) {
    return new NotificationPreferenceResponse(
        entity.isEmailEnabled(),
        entity.isInAppEnabled(),
        entity.getEventPreferences(),
        entity.getQuietHoursStart(),
        entity.getQuietHoursEnd(),
        entity.getQuietHoursTz(),
        entity.getUpdatedAt());
  }

  public static NotificationPreferenceResponse defaults() {
    return new NotificationPreferenceResponse(true, true, "{}", null, null, null, Instant.now());
  }
}

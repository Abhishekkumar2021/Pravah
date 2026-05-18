package io.pravah.notification.application;

import io.pravah.notification.api.dto.NotificationPreferenceResponse;
import io.pravah.notification.api.dto.UpdateNotificationPreferenceRequest;
import io.pravah.notification.infrastructure.persistence.entity.NotificationPreferenceEntity;
import io.pravah.notification.infrastructure.persistence.repository.NotificationPreferenceRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository preferenceRepository;

  public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
    this.preferenceRepository = preferenceRepository;
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

  @Transactional(readOnly = true)
  public NotificationPreferenceResponse getPreferences() {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    return preferenceRepository
        .findByTenantIdAndUserId(tenantId, userId)
        .map(NotificationPreferenceResponse::from)
        .orElseGet(NotificationPreferenceResponse::defaults);
  }

  @Transactional
  public NotificationPreferenceResponse updatePreferences(
      UpdateNotificationPreferenceRequest request) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    NotificationPreferenceEntity entity =
        preferenceRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseGet(() -> new NotificationPreferenceEntity(tenantId, userId));

    NotificationPreferenceResponse current = NotificationPreferenceResponse.from(entity);

    entity.update(
        request.emailEnabled() != null ? request.emailEnabled() : current.emailEnabled(),
        request.inAppEnabled() != null ? request.inAppEnabled() : current.inAppEnabled(),
        request.eventPreferences() != null
            ? request.eventPreferences()
            : current.eventPreferences(),
        parseTime(request.quietHoursStart(), current.quietHoursStart()),
        parseTime(request.quietHoursEnd(), current.quietHoursEnd()),
        request.quietHoursTz() != null ? request.quietHoursTz() : current.quietHoursTz());

    preferenceRepository.save(entity);
    return NotificationPreferenceResponse.from(entity);
  }

  private static LocalTime parseTime(String value, LocalTime fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return LocalTime.parse(value);
  }
}

package io.pravah.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.notification.api.dto.UpdateNotificationPreferenceRequest;
import io.pravah.notification.infrastructure.persistence.entity.NotificationPreferenceEntity;
import io.pravah.notification.infrastructure.persistence.repository.NotificationPreferenceRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

  @Mock private NotificationPreferenceRepository preferenceRepository;

  private NotificationPreferenceService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new NotificationPreferenceService(preferenceRepository);
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getPreferences_returnsDefaultsWhenMissing() {
    when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(Optional.empty());

    var response = service.getPreferences();

    assertThat(response.emailEnabled()).isTrue();
    assertThat(response.inAppEnabled()).isTrue();
    assertThat(response.eventPreferences()).isEqualTo("{}");
  }

  @Test
  void updatePreferences_createsEntityWhenMissing() {
    when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(Optional.empty());
    when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.updatePreferences(
            new UpdateNotificationPreferenceRequest(false, true, null, null, null, null));

    assertThat(response.emailEnabled()).isFalse();
    assertThat(response.inAppEnabled()).isTrue();

    ArgumentCaptor<NotificationPreferenceEntity> captor =
        ArgumentCaptor.forClass(NotificationPreferenceEntity.class);
    verify(preferenceRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
  }

  @Test
  void updatePreferences_mergesPartialUpdate() {
    var existing = new NotificationPreferenceEntity(tenantId, userId);
    when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(Optional.of(existing));
    when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.updatePreferences(
            new UpdateNotificationPreferenceRequest(null, false, null, "22:00", "07:00", "UTC"));

    assertThat(response.emailEnabled()).isTrue();
    assertThat(response.inAppEnabled()).isFalse();
    assertThat(response.quietHoursStart().toString()).isEqualTo("22:00");
    assertThat(response.quietHoursEnd().toString()).isEqualTo("07:00");
    assertThat(response.quietHoursTz()).isEqualTo("UTC");
  }
}

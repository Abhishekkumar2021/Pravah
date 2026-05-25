package io.pravah.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.notification.api.dto.CreateAlertRuleRequest;
import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import io.pravah.notification.infrastructure.persistence.repository.AlertRuleRepository;
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
class AlertRuleApplicationServiceTest {

  @Mock private AlertRuleRepository alertRuleRepository;

  private AlertRuleApplicationService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AlertRuleApplicationService(alertRuleRepository);
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createAlertRule_persistsEntity() {
    when(alertRuleRepository.existsByTenantIdAndName(tenantId, "ops-alert")).thenReturn(false);
    when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.createAlertRule(
            new CreateAlertRuleRequest(
                null,
                "ops-alert",
                "desc",
                "{\"events\":[\"execution.failed\"]}",
                "[{\"type\":\"email\",\"recipients\":[\"a@b.com\"]}]",
                300));

    assertThat(response.name()).isEqualTo("ops-alert");
    assertThat(response.enabled()).isTrue();

    ArgumentCaptor<AlertRuleEntity> captor = ArgumentCaptor.forClass(AlertRuleEntity.class);
    verify(alertRuleRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getCreatedBy()).isEqualTo(userId);
  }

  @Test
  void createAlertRule_rejectsDuplicateName() {
    when(alertRuleRepository.existsByTenantIdAndName(tenantId, "dup")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.createAlertRule(
                    new CreateAlertRuleRequest(null, "dup", null, "{}", "[]", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void toggleAlertRule_updatesEnabledFlag() {
    UUID ruleId = UUID.randomUUID();
    AlertRuleEntity entity =
        new AlertRuleEntity(tenantId, null, "rule", null, "{}", "[]", 300, userId);
    when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(entity));

    var response = service.toggleAlertRule(ruleId, false);

    assertThat(response.enabled()).isFalse();
    assertThat(entity.isEnabled()).isFalse();
  }
}

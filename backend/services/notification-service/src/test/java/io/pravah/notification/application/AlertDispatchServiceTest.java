package io.pravah.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pravah.notification.application.dto.AlertContext;
import io.pravah.notification.infrastructure.channel.DeliveryResult;
import io.pravah.notification.infrastructure.channel.NotificationChannel;
import io.pravah.notification.infrastructure.persistence.entity.AlertHistoryEntity;
import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import io.pravah.notification.infrastructure.persistence.repository.AlertHistoryRepository;
import io.pravah.notification.infrastructure.persistence.repository.AlertRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertDispatchServiceTest {

  @Mock private AlertRuleRepository alertRuleRepository;
  @Mock private AlertHistoryRepository alertHistoryRepository;
  @Mock private UserNotificationService userNotificationService;
  @Mock private NotificationChannel emailChannel;

  private AlertDispatchService service;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private final UUID tenantId = UUID.randomUUID();
  private final UUID pipelineId = UUID.randomUUID();
  private final UUID ruleId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(emailChannel.type()).thenReturn("email");
    service =
        new AlertDispatchService(
            alertRuleRepository,
            alertHistoryRepository,
            userNotificationService,
            List.of(emailChannel),
            objectMapper,
            "http://localhost:5173");
  }

  @Test
  void processAlert_dispatchesWhenRuleMatches() {
    AlertRuleEntity rule =
        new AlertRuleEntity(
            tenantId,
            pipelineId,
            "failures",
            "desc",
            "{\"events\":[\"execution.failed\"]}",
            "[{\"type\":\"email\",\"recipients\":[\"ops@example.com\"]}]",
            300,
            UUID.randomUUID());

    when(alertRuleRepository.findEnabledRulesForPipeline(tenantId, pipelineId))
        .thenReturn(List.of(rule));
    when(alertHistoryRepository.existsRecentDuplicate(
            eq(tenantId), eq(rule.getId()), any(), any(Instant.class)))
        .thenReturn(false);
    when(emailChannel.send(any(), any())).thenReturn(DeliveryResult.success("email"));

    AlertContext context =
        new AlertContext(
            tenantId,
            UUID.randomUUID(),
            pipelineId,
            "etl",
            "execution.failed",
            "FAILED",
            "boom",
            null,
            Instant.now(),
            "prod",
            Map.of(),
            null);

    service.processAlert(tenantId, pipelineId, context);

    ArgumentCaptor<AlertHistoryEntity> historyCaptor =
        ArgumentCaptor.forClass(AlertHistoryEntity.class);
    verify(alertHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getEventType()).isEqualTo("execution.failed");
    verify(emailChannel).send(any(), any());
  }

  @Test
  void processAlert_skipsWhenNoRules() {
    when(alertRuleRepository.findEnabledRulesForPipeline(tenantId, pipelineId))
        .thenReturn(List.of());

    service.processAlert(
        tenantId,
        pipelineId,
        new AlertContext(
            tenantId,
            UUID.randomUUID(),
            pipelineId,
            "etl",
            "execution.failed",
            "FAILED",
            null,
            null,
            Instant.now(),
            null,
            Map.of(),
            null));

    verify(alertHistoryRepository, never()).save(any());
    verify(emailChannel, never()).send(any(), any());
  }

  @Test
  void processAlert_skipsWhenEventNotInConditions() {
    AlertRuleEntity rule =
        new AlertRuleEntity(
            tenantId,
            pipelineId,
            "completed-only",
            null,
            "{\"events\":[\"execution.completed\"]}",
            "[{\"type\":\"email\",\"recipients\":[\"ops@example.com\"]}]",
            300,
            UUID.randomUUID());

    when(alertRuleRepository.findEnabledRulesForPipeline(tenantId, pipelineId))
        .thenReturn(List.of(rule));

    service.processAlert(
        tenantId,
        pipelineId,
        new AlertContext(
            tenantId,
            UUID.randomUUID(),
            pipelineId,
            "etl",
            "execution.failed",
            "FAILED",
            null,
            null,
            Instant.now(),
            null,
            Map.of(),
            null));

    verify(alertHistoryRepository, never()).save(any());
    verify(emailChannel, never()).send(any(), any());
  }
}

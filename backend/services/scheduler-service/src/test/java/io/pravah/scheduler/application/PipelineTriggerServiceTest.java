package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.ValidationException;
import io.pravah.scheduler.api.dto.CreatePipelineTriggerRequest;
import io.pravah.scheduler.api.dto.PipelineTriggerResponse;
import io.pravah.scheduler.api.dto.UpdatePipelineTriggerRequest;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.kafka.KafkaTriggerListenerManager;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchHistoryRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PipelineTriggerServiceTest {

  @Mock private PipelineTriggerRepository triggerRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTriggerDispatchService dispatchService;
  @Mock private TriggerDispatchHistoryRepository dispatchHistoryRepository;

  private PipelineTriggerService service;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    service =
        new PipelineTriggerService(
            triggerRepository,
            encoder,
            new WebhookSecretGenerator(),
            "http://localhost:8080/api/v1/hooks",
            eventPublisher,
            dispatchService,
            dispatchHistoryRepository);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createTrigger_webhook_generatesSecretAndUrl() {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, "my-hook", "webhook", Map.of());

    when(triggerRepository.existsByTenantIdAndPipelineIdAndName(tenantId, pipelineId, "my-hook"))
        .thenReturn(false);
    when(triggerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PipelineTriggerResponse response = service.createTrigger(request);

    assertThat(response.name()).isEqualTo("my-hook");
    assertThat(response.triggerType()).isEqualTo("webhook");
    assertThat(response.webhookUrl()).startsWith("http://localhost:8080/api/v1/hooks/");
    assertThat(response.webhookSecret()).isNotBlank();
    assertThat(response.enabled()).isTrue();

    verifyTriggersChangedEventPublished();
  }

  @Test
  void createTrigger_kafka_requiresTopic() {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, "kafka-trigger", "kafka", Map.of());

    assertThatThrownBy(() -> service.createTrigger(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Kafka trigger requires config.topic");
  }

  @Test
  void createTrigger_kafka_acceptsValidConfig() {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(
            pipelineId, "kafka-trigger", "kafka", Map.of("topic", "orders"));

    when(triggerRepository.existsByTenantIdAndPipelineIdAndName(
            tenantId, pipelineId, "kafka-trigger"))
        .thenReturn(false);
    when(triggerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PipelineTriggerResponse response = service.createTrigger(request);

    assertThat(response.name()).isEqualTo("kafka-trigger");
    assertThat(response.triggerType()).isEqualTo("kafka");
    assertThat(response.webhookUrl()).isNull();
    assertThat(response.webhookSecret()).isNull();
  }

  @Test
  void createTrigger_rejectsDuplicateName() {
    UUID pipelineId = UUID.randomUUID();
    CreatePipelineTriggerRequest request =
        new CreatePipelineTriggerRequest(pipelineId, "existing", "webhook", Map.of());

    when(triggerRepository.existsByTenantIdAndPipelineIdAndName(tenantId, pipelineId, "existing"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.createTrigger(request))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e -> {
              ValidationException ve = (ValidationException) e;
              assertThat(ve.getFieldErrors()).hasSize(1);
              assertThat(ve.getFieldErrors().get(0).message())
                  .contains("Trigger name already exists");
            });

    verify(triggerRepository, never()).save(any());
  }

  @Test
  void disableTrigger_setsFlagAndPublishesEvent() {
    UUID triggerId = UUID.randomUUID();
    PipelineTrigger trigger = createWebhookTrigger(triggerId);
    when(triggerRepository.findByIdAndTenantId(triggerId, tenantId))
        .thenReturn(Optional.of(trigger));
    when(triggerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PipelineTriggerResponse response = service.disableTrigger(triggerId);

    assertThat(response.enabled()).isFalse();
    verifyTriggersChangedEventPublished();
  }

  @Test
  void enableTrigger_setsFlagAndPublishesEvent() {
    UUID triggerId = UUID.randomUUID();
    PipelineTrigger trigger = createWebhookTrigger(triggerId);
    trigger.disable();
    when(triggerRepository.findByIdAndTenantId(triggerId, tenantId))
        .thenReturn(Optional.of(trigger));
    when(triggerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PipelineTriggerResponse response = service.enableTrigger(triggerId);

    assertThat(response.enabled()).isTrue();
    verifyTriggersChangedEventPublished();
  }

  @Test
  void updateTrigger_updatesNameAndConfig() {
    UUID triggerId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    PipelineTrigger trigger =
        PipelineTrigger.builder()
            .tenantId(tenantId)
            .pipelineId(pipelineId)
            .name("old-name")
            .triggerType(TriggerType.WEBHOOK)
            .config("{}")
            .createdBy(userId)
            .build();

    when(triggerRepository.findByIdAndTenantId(triggerId, tenantId))
        .thenReturn(Optional.of(trigger));
    when(triggerRepository.existsByTenantIdAndPipelineIdAndName(tenantId, pipelineId, "new-name"))
        .thenReturn(false);
    when(triggerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UpdatePipelineTriggerRequest request =
        new UpdatePipelineTriggerRequest("new-name", Map.of("rateLimitPerMinute", 30), null);

    PipelineTriggerResponse response = service.updateTrigger(triggerId, request);

    assertThat(response.name()).isEqualTo("new-name");
    assertThat(response.config()).containsEntry("rateLimitPerMinute", 30);
    verifyTriggersChangedEventPublished();
  }

  private void verifyTriggersChangedEventPublished() {
    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isInstanceOf(KafkaTriggerListenerManager.TriggersChangedEvent.class);
  }

  private PipelineTrigger createWebhookTrigger(UUID triggerId) {
    return PipelineTrigger.builder()
        .tenantId(tenantId)
        .pipelineId(UUID.randomUUID())
        .name("test-hook")
        .triggerType(TriggerType.WEBHOOK)
        .config("{}")
        .secretHash(encoder.encode("test-secret"))
        .createdBy(userId)
        .build();
  }
}

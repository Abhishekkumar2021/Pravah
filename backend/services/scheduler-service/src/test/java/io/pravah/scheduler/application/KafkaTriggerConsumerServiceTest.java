package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.infrastructure.persistence.entity.KafkaTriggerProcessedEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.KafkaTriggerProcessedRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaTriggerConsumerServiceTest {

  @Mock private KafkaTriggerProcessedRepository processedRepository;
  @Mock private PipelineTriggerDispatchService dispatchService;

  @InjectMocks private KafkaTriggerConsumerService consumerService;

  @Test
  void processTriggerMessage_skipsWhenAlreadyProcessed() {
    UUID triggerId = UUID.randomUUID();
    PipelineTrigger trigger = trigger(triggerId);

    when(processedRepository.existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
            triggerId, "orders", 0, 42L))
        .thenReturn(true);

    consumerService.processTriggerMessage(
        trigger, "orders", 0, 42L, Map.of("type", "created"), "key-1");

    verify(processedRepository, never()).save(any());
    verify(dispatchService, never()).dispatch(any(), any(), any());
  }

  @Test
  void processTriggerMessage_claimsBeforeDispatch() {
    UUID triggerId = UUID.randomUUID();
    PipelineTrigger trigger = trigger(triggerId);
    Map<String, Object> payload = Map.of("type", "created");

    when(processedRepository.existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
            triggerId, "orders", 1, 7L))
        .thenReturn(false);

    consumerService.processTriggerMessage(trigger, "orders", 1, 7L, payload, "key-2");

    ArgumentCaptor<KafkaTriggerProcessedEntity> saved =
        ArgumentCaptor.forClass(KafkaTriggerProcessedEntity.class);
    verify(processedRepository).save(saved.capture());
    verify(dispatchService).dispatch(trigger, payload, "key-2");
    assertThat(saved.getValue().getTriggerId()).isEqualTo(triggerId);
    assertThat(saved.getValue().getTopic()).isEqualTo("orders");
  }

  @Test
  void processTriggerMessage_deletesClaimOnDispatchFailure() {
    UUID triggerId = UUID.randomUUID();
    PipelineTrigger trigger = trigger(triggerId);

    when(processedRepository.existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
            triggerId, "orders", 2, 9L))
        .thenReturn(false);
    org.mockito.Mockito.doThrow(new RuntimeException("dispatch failed"))
        .when(dispatchService)
        .dispatch(any(), any(), any());

    assertThatThrownBy(
            () ->
                consumerService.processTriggerMessage(trigger, "orders", 2, 9L, Map.of(), "key-3"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("dispatch failed");

    verify(processedRepository)
        .deleteByTriggerIdAndTopicAndPartitionNumAndOffsetNum(triggerId, "orders", 2, 9L);
  }

  private static PipelineTrigger trigger(UUID id) {
    PipelineTrigger trigger = org.mockito.Mockito.mock(PipelineTrigger.class);
    when(trigger.getId()).thenReturn(id);
    return trigger;
  }
}

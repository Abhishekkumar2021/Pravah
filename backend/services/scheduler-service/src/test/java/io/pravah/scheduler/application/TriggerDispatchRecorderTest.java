package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchHistoryEntity;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchPendingEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchHistoryRepository;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchPendingRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerDispatchRecorderTest {

  @Mock private TriggerDispatchHistoryRepository historyRepository;
  @Mock private TriggerDispatchPendingRepository pendingRepository;

  private TriggerDispatchRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder =
        new TriggerDispatchRecorder(historyRepository, pendingRepository, new ObjectMapper());
  }

  @Test
  void recordFailure_skipsDuplicateActivePending() {
    PipelineTrigger trigger = sampleTrigger();
    when(pendingRepository.existsByTriggerIdAndStatusIn(
            trigger.getId(), java.util.List.of("pending", "failed")))
        .thenReturn(true);

    recorder.recordFailure(trigger, Map.of("k", "v"), "boom", true, null);

    verify(historyRepository).save(any(TriggerDispatchHistoryEntity.class));
    verify(pendingRepository, never()).save(any(TriggerDispatchPendingEntity.class));
  }

  @Test
  void recordFailure_createsPendingWhenNoneActive() {
    PipelineTrigger trigger = sampleTrigger();
    when(pendingRepository.existsByTriggerIdAndStatusIn(
            trigger.getId(), java.util.List.of("pending", "failed")))
        .thenReturn(false);

    recorder.recordFailure(trigger, Map.of("k", "v"), "boom", true, null);

    ArgumentCaptor<TriggerDispatchPendingEntity> captor =
        ArgumentCaptor.forClass(TriggerDispatchPendingEntity.class);
    verify(pendingRepository).save(captor.capture());
    assertThat(captor.getValue().getTriggerId()).isEqualTo(trigger.getId());
  }

  private static PipelineTrigger sampleTrigger() {
    return PipelineTrigger.builder()
        .tenantId(UUID.randomUUID())
        .pipelineId(UUID.randomUUID())
        .name("hook")
        .triggerType(TriggerType.WEBHOOK)
        .config("{}")
        .createdBy(UUID.randomUUID())
        .build();
  }
}

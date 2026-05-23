package io.pravah.scheduler.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.leader.LeaderElectionService;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchPendingEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchPendingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerDispatchOutboxRelayTest {

  private static final Instant NOW = Instant.parse("2026-05-16T12:00:00Z");

  @Mock private LeaderElectionService leaderElectionService;
  @Mock private TriggerDispatchPendingRepository pendingRepository;
  @Mock private PipelineTriggerRepository triggerRepository;
  @Mock private PipelineTriggerDispatchService dispatchService;
  @Mock private TriggerDispatchRecorder recorder;

  private TriggerDispatchOutboxRelay relay;

  @BeforeEach
  void setUp() {
    relay =
        new TriggerDispatchOutboxRelay(
            leaderElectionService,
            pendingRepository,
            triggerRepository,
            dispatchService,
            recorder,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            5);
  }

  @Test
  void relayPending_skipsWhenNotLeader() {
    when(leaderElectionService.isLeader()).thenReturn(false);

    relay.relayPending();

    verify(pendingRepository, never()).findDueForRetry(any());
  }

  @Test
  void relayPending_marksExhaustedOnTenantMismatch() {
    UUID tenantId = UUID.randomUUID();
    PipelineTrigger trigger = sampleTrigger(UUID.randomUUID());
    TriggerDispatchPendingEntity pending =
        TriggerDispatchPendingEntity.pending(
            tenantId, trigger.getId(), trigger.getPipelineId(), "webhook", "{}", null);

    when(leaderElectionService.isLeader()).thenReturn(true);
    when(pendingRepository.findDueForRetry(NOW)).thenReturn(List.of(pending));
    when(triggerRepository.findById(trigger.getId())).thenReturn(Optional.of(trigger));

    relay.relayPending();

    assertThatPendingExhausted(pending);
    verify(dispatchService, never()).dispatchWithoutRetry(any(), any(), any());
  }

  @Test
  void relayPending_dispatchesAndCompletesPending() {
    UUID tenantId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    PipelineTrigger trigger = sampleTrigger(tenantId);
    TriggerDispatchPendingEntity pending =
        TriggerDispatchPendingEntity.pending(
            tenantId,
            trigger.getId(),
            trigger.getPipelineId(),
            "webhook",
            "{\"k\":\"v\"}",
            "idem-1");

    when(leaderElectionService.isLeader()).thenReturn(true);
    when(pendingRepository.findDueForRetry(NOW)).thenReturn(List.of(pending));
    when(triggerRepository.findById(trigger.getId())).thenReturn(Optional.of(trigger));
    when(dispatchService.dispatchWithoutRetry(trigger, Map.of("k", "v"), "idem-1"))
        .thenReturn(executionId);

    relay.relayPending();

    verify(recorder).recordSuccess(trigger, Map.of("k", "v"), executionId);
    org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("completed");
  }

  private static void assertThatPendingExhausted(TriggerDispatchPendingEntity pending) {
    org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("exhausted");
  }

  private static PipelineTrigger sampleTrigger(UUID tenantId) {
    return PipelineTrigger.builder()
        .tenantId(tenantId)
        .pipelineId(UUID.randomUUID())
        .name("hook")
        .triggerType(TriggerType.WEBHOOK)
        .config("{}")
        .createdBy(UUID.randomUUID())
        .build();
  }
}

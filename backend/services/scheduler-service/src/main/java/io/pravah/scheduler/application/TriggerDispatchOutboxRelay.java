package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.leader.LeaderElectionService;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchPendingEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchPendingRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retries failed webhook/Kafka trigger dispatches (outbox pattern). */
@Component
public class TriggerDispatchOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(TriggerDispatchOutboxRelay.class);

  private final LeaderElectionService leaderElectionService;
  private final TriggerDispatchPendingRepository pendingRepository;
  private final PipelineTriggerRepository triggerRepository;
  private final PipelineTriggerDispatchService dispatchService;
  private final TriggerDispatchRecorder recorder;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final int maxAttempts;

  public TriggerDispatchOutboxRelay(
      LeaderElectionService leaderElectionService,
      TriggerDispatchPendingRepository pendingRepository,
      PipelineTriggerRepository triggerRepository,
      PipelineTriggerDispatchService dispatchService,
      TriggerDispatchRecorder recorder,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${pravah.scheduler.trigger-retry.max-attempts:5}") int maxAttempts) {
    this.leaderElectionService = leaderElectionService;
    this.pendingRepository = pendingRepository;
    this.triggerRepository = triggerRepository;
    this.dispatchService = dispatchService;
    this.recorder = recorder;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${pravah.scheduler.trigger-retry.interval-ms:30000}")
  @Transactional
  public void relayPending() {
    if (!leaderElectionService.isLeader()) {
      return;
    }
    List<TriggerDispatchPendingEntity> due = pendingRepository.findDueForRetry(clock.instant());
    for (TriggerDispatchPendingEntity pending : due) {
      if (pending.getAttempts() >= maxAttempts) {
        pending.markExhausted();
        pendingRepository.save(pending);
        log.warn(
            "Trigger dispatch retry exhausted",
            kv("pending_id", pending.getId()),
            kv("trigger_id", pending.getTriggerId()));
        continue;
      }
      PipelineTrigger trigger = triggerRepository.findById(pending.getTriggerId()).orElse(null);
      if (trigger == null
          || !trigger.isEnabled()
          || !trigger.getTenantId().equals(pending.getTenantId())) {
        pending.markExhausted();
        pendingRepository.save(pending);
        continue;
      }
      TenantContext.setCurrentTenantId(pending.getTenantId());
      try {
        Map<String, Object> payload = parsePayload(pending.getPayload());
        UUID executionId =
            dispatchService.dispatchWithoutRetry(trigger, payload, pending.getIdempotencyKey());
        recorder.recordSuccess(trigger, payload, executionId);
        pending.markCompleted();
        pendingRepository.save(pending);
      } catch (Exception e) {
        Instant next =
            clock.instant().plusSeconds(Math.min(300, (long) Math.pow(2, pending.getAttempts())));
        pending.markFailed(e.getMessage(), next);
        pendingRepository.save(pending);
        log.warn(
            "Trigger dispatch retry failed",
            kv("pending_id", pending.getId()),
            kv("trigger_id", pending.getTriggerId()),
            e);
      } finally {
        TenantContext.clear();
      }
    }
  }

  private Map<String, Object> parsePayload(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("Failed to parse trigger dispatch payload", kv("error", e.getMessage()));
      return Map.of();
    }
  }
}

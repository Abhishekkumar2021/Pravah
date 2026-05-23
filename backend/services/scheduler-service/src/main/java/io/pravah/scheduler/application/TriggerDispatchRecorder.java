package io.pravah.scheduler.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchHistoryEntity;
import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchPendingEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchHistoryRepository;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchPendingRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TriggerDispatchRecorder {

  private static final List<String> ACTIVE_PENDING_STATUSES = List.of("pending", "failed");

  private final TriggerDispatchHistoryRepository historyRepository;
  private final TriggerDispatchPendingRepository pendingRepository;
  private final ObjectMapper objectMapper;

  public TriggerDispatchRecorder(
      TriggerDispatchHistoryRepository historyRepository,
      TriggerDispatchPendingRepository pendingRepository,
      ObjectMapper objectMapper) {
    this.historyRepository = historyRepository;
    this.pendingRepository = pendingRepository;
    this.objectMapper = objectMapper;
  }

  public void recordSuccess(
      PipelineTrigger trigger, Map<String, Object> payload, UUID executionId) {
    historyRepository.save(
        new TriggerDispatchHistoryEntity(
            trigger.getTenantId(),
            trigger.getId(),
            trigger.getPipelineId(),
            trigger.getTriggerType().value(),
            "success",
            executionId,
            null,
            toJson(payload)));
  }

  public void recordFailure(
      PipelineTrigger trigger,
      Map<String, Object> payload,
      String error,
      boolean scheduleRetry,
      String idempotencyKey) {
    historyRepository.save(
        new TriggerDispatchHistoryEntity(
            trigger.getTenantId(),
            trigger.getId(),
            trigger.getPipelineId(),
            trigger.getTriggerType().value(),
            "failed",
            null,
            error,
            toJson(payload)));
    if (scheduleRetry && !hasActivePending(trigger.getId(), idempotencyKey)) {
      pendingRepository.save(
          TriggerDispatchPendingEntity.pending(
              trigger.getTenantId(),
              trigger.getId(),
              trigger.getPipelineId(),
              trigger.getTriggerType().value(),
              toJson(payload),
              idempotencyKey));
    }
  }

  private boolean hasActivePending(UUID triggerId, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return pendingRepository.existsByTriggerIdAndIdempotencyKeyAndStatusIn(
          triggerId, idempotencyKey, ACTIVE_PENDING_STATUSES);
    }
    return pendingRepository.existsByTriggerIdAndStatusIn(triggerId, ACTIVE_PENDING_STATUSES);
  }

  private String toJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}

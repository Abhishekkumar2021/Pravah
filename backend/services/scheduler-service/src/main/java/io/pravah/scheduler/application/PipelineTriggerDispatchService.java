package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.client.ExecutionTriggerClient;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dispatches pipeline runs for webhook and Kafka triggers. */
@Service
public class PipelineTriggerDispatchService {

  private static final Logger log = LoggerFactory.getLogger(PipelineTriggerDispatchService.class);

  private final ExecutionTriggerClient executionTriggerClient;
  private final PipelineTriggerRepository triggerRepository;
  private final TriggerDispatchRecorder dispatchRecorder;

  public PipelineTriggerDispatchService(
      ExecutionTriggerClient executionTriggerClient,
      PipelineTriggerRepository triggerRepository,
      TriggerDispatchRecorder dispatchRecorder) {
    this.executionTriggerClient = executionTriggerClient;
    this.triggerRepository = triggerRepository;
    this.dispatchRecorder = dispatchRecorder;
  }

  @Transactional
  public UUID dispatch(PipelineTrigger trigger, Map<String, Object> payload) {
    return dispatch(trigger, payload, null);
  }

  @Transactional
  public UUID dispatch(
      PipelineTrigger trigger, Map<String, Object> payload, String idempotencyKey) {
    try {
      UUID executionId = dispatchWithoutRetry(trigger, payload, idempotencyKey);
      dispatchRecorder.recordSuccess(trigger, payload, executionId);
      return executionId;
    } catch (RuntimeException e) {
      dispatchRecorder.recordFailure(trigger, payload, e.getMessage(), true, idempotencyKey);
      throw e;
    }
  }

  @Transactional
  public UUID dispatchWithoutRetry(
      PipelineTrigger trigger, Map<String, Object> payload, String idempotencyKey) {
    Map<String, Object> parameters = buildParameters(trigger, payload);
    TenantContext.setCurrentTenantId(trigger.getTenantId());
    try {
      UUID executionId =
          executionTriggerClient.triggerEventExecution(
              trigger.getTenantId(),
              trigger.getPipelineId(),
              trigger.getTriggerType().value(),
              trigger.getId(),
              parameters,
              idempotencyKey);
      trigger.recordTriggered();
      triggerRepository.save(trigger);
      log.info(
          "Triggered pipeline from event",
          kv("trigger_id", trigger.getId()),
          kv("trigger_type", trigger.getTriggerType().value()),
          kv("pipeline_id", trigger.getPipelineId()),
          kv("execution_id", executionId));
      return executionId;
    } finally {
      TenantContext.clear();
    }
  }

  private static Map<String, Object> buildParameters(
      PipelineTrigger trigger, Map<String, Object> payload) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    if (payload != null && !payload.isEmpty()) {
      parameters.put("trigger", Map.copyOf(payload));
    }
    parameters.put(
        "_meta",
        Map.of(
            "triggerId", trigger.getId().toString(),
            "triggerType", trigger.getTriggerType().value()));
    if (trigger.getTriggerType() == TriggerType.KAFKA) {
      Map<String, Object> config = TriggerConfigSupport.parseConfig(trigger.getConfig());
      String topic = TriggerConfigSupport.requireKafkaTopic(config);
      parameters.put("kafkaTopic", topic);
    }
    return Map.copyOf(parameters);
  }
}

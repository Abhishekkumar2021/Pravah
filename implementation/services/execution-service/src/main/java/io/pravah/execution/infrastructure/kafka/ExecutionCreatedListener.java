package io.pravah.execution.infrastructure.kafka;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.application.ExecutionCreatedProcessingService;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Consumes {@code execution.created} from Kafka (at-least-once; handler is idempotent). */
@Component
@ConditionalOnProperty(
    name = "pravah.kafka.execution-created-listener-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExecutionCreatedListener {

  private static final Logger log = LoggerFactory.getLogger(ExecutionCreatedListener.class);

  private final ExecutionCreatedProcessingService processingService;

  public ExecutionCreatedListener(ExecutionCreatedProcessingService processingService) {
    this.processingService = processingService;
  }

  @KafkaListener(
      topics = "${pravah.outbox.topic.execution-events}",
      groupId = "${spring.kafka.consumer.group-id}",
      containerFactory = "executionKafkaListenerContainerFactory")
  public void onMessage(Map<String, Object> payload, Acknowledgment acknowledgment) {
    Object eventTypeRaw = payload.get("eventType");
    String eventType = eventTypeRaw != null ? eventTypeRaw.toString() : null;
    if (!ExecutionEventTypes.EXECUTION_CREATED.equals(eventType)) {
      log.debug("Skipping non-execution.created message", kv("event_type", eventTypeRaw));
      acknowledgment.acknowledge();
      return;
    }
    Object eventIdRaw = payload.get("eventId");
    if (eventIdRaw == null) {
      log.warn("execution.created missing eventId; acknowledging to avoid poison pill loop");
      acknowledgment.acknowledge();
      return;
    }

    Object tenantRaw = payload.get("tenantId");
    if (tenantRaw == null) {
      log.warn("execution.created missing tenantId; acknowledging to avoid poison pill loop");
      acknowledgment.acknowledge();
      return;
    }
    UUID tenantId = UUID.fromString(tenantRaw.toString());
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(null);
      processingService.processExecutionCreated(payload);
      acknowledgment.acknowledge();
    } finally {
      TenantContext.clear();
    }
  }
}

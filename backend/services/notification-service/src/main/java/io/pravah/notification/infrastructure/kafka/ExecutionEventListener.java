package io.pravah.notification.infrastructure.kafka;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.notification.application.AlertDispatchService;
import io.pravah.notification.application.AuditLogService;
import io.pravah.notification.application.KafkaEventIngestionService;
import io.pravah.notification.application.dto.AlertContext;
import io.pravah.notification.domain.ActorType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for execution events from execution-service. Triggers alerts based on event type
 * (failed, timeout, completed, cancelled).
 */
@Component
public class ExecutionEventListener {

  private static final Logger log = LoggerFactory.getLogger(ExecutionEventListener.class);

  private final KafkaEventIngestionService kafkaEventIngestionService;
  private final AlertDispatchService alertDispatchService;
  private final AuditLogService auditLogService;

  public ExecutionEventListener(
      KafkaEventIngestionService kafkaEventIngestionService,
      AlertDispatchService alertDispatchService,
      AuditLogService auditLogService) {
    this.kafkaEventIngestionService = kafkaEventIngestionService;
    this.alertDispatchService = alertDispatchService;
    this.auditLogService = auditLogService;
  }

  @KafkaListener(
      topics = "${pravah.kafka.topics.execution-events:pravah.execution.execution.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onExecutionEvent(Map<String, Object> event) {
    try {
      kafkaEventIngestionService.processIfNew(
          event,
          payload -> {
            String eventType = (String) payload.get("eventType");
            String alertEventType = mapToAlertEventType(eventType, payload);
            if (alertEventType == null) {
              log.debug("Event type not alertable", kv("event_type", eventType));
              return;
            }

            UUID tenantId = parseUuid(payload.get("tenantId"));
            UUID executionId = parseUuid(payload.get("executionId"));
            UUID pipelineId = parseUuid(payload.get("pipelineId"));

            if (tenantId == null) {
              log.warn("Event missing tenantId", kv("event_type", eventType));
              return;
            }

            String pipelineName = (String) payload.get("pipelineName");
            String status = (String) payload.get("status");
            String errorMessage = extractErrorMessage(payload);
            String environment = (String) payload.get("environment");

            AlertContext context =
                new AlertContext(
                    tenantId,
                    executionId,
                    pipelineId,
                    pipelineName,
                    alertEventType,
                    status,
                    errorMessage,
                    null,
                    Instant.now(),
                    environment,
                    payload,
                    null);

            log.info(
                "Processing execution event for alerts",
                kv("event_type", alertEventType),
                kv("execution_id", executionId),
                kv("pipeline_id", pipelineId));

            alertDispatchService.processAlert(tenantId, pipelineId, context);

            auditLogService.logEvent(
                tenantId,
                null,
                ActorType.SYSTEM,
                "system",
                alertEventType,
                "execution",
                executionId,
                pipelineName,
                payload,
                null,
                null,
                null);
          });
    } catch (Exception e) {
      log.error("Failed to process execution event", e);
    }
  }

  private String mapToAlertEventType(String eventType, Map<String, Object> event) {
    if (eventType == null) {
      return null;
    }
    return switch (eventType) {
      case "execution.created" -> null;
      case "execution.cancelled" -> "execution.cancelled";
      case "execution.failed" -> "execution.failed";
      case "execution.completed" -> "execution.completed";
      default -> {
        String status = (String) event.get("status");
        if ("FAILED".equalsIgnoreCase(status)) {
          yield "execution.failed";
        } else if ("COMPLETED".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status)) {
          yield "execution.completed";
        } else if ("TIMED_OUT".equalsIgnoreCase(status)) {
          yield "execution.timeout";
        }
        yield null;
      }
    };
  }

  private UUID parseUuid(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof UUID uuid) {
      return uuid;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private String extractErrorMessage(Map<String, Object> event) {
    Object error = event.get("errorMessage");
    if (error != null) {
      return error.toString();
    }
    error = event.get("error");
    if (error != null) {
      return error.toString();
    }
    Object failureReason = event.get("failureReason");
    if (failureReason != null) {
      return failureReason.toString();
    }
    return null;
  }
}

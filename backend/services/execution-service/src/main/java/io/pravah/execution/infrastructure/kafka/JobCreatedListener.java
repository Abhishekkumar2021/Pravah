package io.pravah.execution.infrastructure.kafka;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.application.JobCreatedProcessingService;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Consumes {@code job.created} and runs the embedded stage worker (LLD §3, MVP co-location). */
@Component
@ConditionalOnProperty(name = "pravah.kafka.job-worker-listener-enabled", havingValue = "true")
public class JobCreatedListener {

  private static final Logger log = LoggerFactory.getLogger(JobCreatedListener.class);

  private final JobCreatedProcessingService processingService;

  public JobCreatedListener(JobCreatedProcessingService processingService) {
    this.processingService = processingService;
  }

  @KafkaListener(
      topics = "${pravah.outbox.topic.job-created}",
      groupId = "${pravah.kafka.job-worker.consumer-group-id}",
      containerFactory = "jobWorkerKafkaListenerContainerFactory")
  public void onMessage(Map<String, Object> payload, Acknowledgment acknowledgment) {
    Object eventTypeRaw = payload.get("eventType");
    String eventType = eventTypeRaw != null ? eventTypeRaw.toString() : null;
    if (!JobEventTypes.JOB_CREATED.equals(eventType)) {
      log.debug("Skipping non-job.created message", kv("event_type", eventTypeRaw));
      acknowledgment.acknowledge();
      return;
    }
    Object eventIdRaw = payload.get("eventId");
    if (eventIdRaw == null) {
      log.warn("job.created missing eventId; acknowledging to avoid poison pill loop");
      acknowledgment.acknowledge();
      return;
    }

    Object tenantRaw = payload.get("tenantId");
    if (tenantRaw == null) {
      log.warn("job.created missing tenantId; acknowledging to avoid poison pill loop");
      acknowledgment.acknowledge();
      return;
    }
    UUID tenantId = UUID.fromString(tenantRaw.toString());
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(null);
      processingService.processJobCreated(payload);
      acknowledgment.acknowledge();
    } finally {
      TenantContext.clear();
    }
  }
}

package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.ExecutionState;
import io.pravah.common.domain.JobState;
import io.pravah.execution.domain.ExecutionEventTypes;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.entity.ProcessedEventEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.execution.infrastructure.persistence.repository.ProcessedEventRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@code execution.created} after it is published to Kafka (self-consume per LLD §2).
 *
 * <p>Implements explicit idempotency via {@link ProcessedEventRepository} (LLD §16: Idempotent
 * Consumer pattern). Also has implicit idempotency: jobs already in {@link JobState#QUEUED} are
 * skipped.
 *
 * <p>The Kafka listener must set {@link TenantContext} tenant (and may leave user unset) before
 * invoking this service.
 */
@Service
public class ExecutionCreatedProcessingService {

  private static final Logger log =
      LoggerFactory.getLogger(ExecutionCreatedProcessingService.class);

  private static final String AGGREGATE_JOB = "job";

  private final ExecutionEntityRepository executionEntityRepository;
  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final String jobCreatedTopic;

  public ExecutionCreatedProcessingService(
      ExecutionEntityRepository executionEntityRepository,
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      ProcessedEventRepository processedEventRepository,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic) {
    this.executionEntityRepository = executionEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.processedEventRepository = processedEventRepository;
    this.jobCreatedTopic = jobCreatedTopic;
  }

  @Transactional
  public void processExecutionCreated(Map<String, Object> payload) {
    UUID eventId = requireUuid(payload, "eventId");

    if (processedEventRepository.existsByEventId(eventId)) {
      log.info("Duplicate event, skipping", kv("event_id", eventId));
      return;
    }

    UUID tenantId = requireUuid(payload, "tenantId");
    UUID executionId = requireUuid(payload, "executionId");
    UUID contextTenant = TenantContext.getCurrentTenantId();
    if (contextTenant == null || !contextTenant.equals(tenantId)) {
      throw new IllegalStateException("TenantContext must match payload tenantId");
    }

    List<String> rootStageIds = toStringList(payload.get("rootStageIds"));
    if (rootStageIds.isEmpty()) {
      log.warn(
          "execution.created missing rootStageIds; nothing to queue",
          kv("execution_id", executionId));
      recordProcessed(eventId);
      return;
    }

    ExecutionEntity execution =
        executionEntityRepository
            .findById(executionId)
            .orElseThrow(
                () -> new IllegalStateException("Execution not found for execution.created"));

    if (!execution.getTenantId().equals(tenantId)) {
      throw new IllegalStateException("Execution tenant mismatch for execution.created");
    }

    if (execution.getStatus() != ExecutionState.PENDING) {
      log.info(
          "execution.created skipped — execution not pending (e.g. cancelled or already started)",
          kv("execution_id", executionId),
          kv("status", execution.getStatus()));
      recordProcessed(eventId);
      return;
    }

    List<JobEntity> jobs = jobEntityRepository.findByExecutionIdOrderByStageIdAsc(executionId);
    boolean anyNewlyQueued = false;
    Instant occurredAt = Instant.now();

    for (JobEntity job : jobs) {
      if (!rootStageIds.contains(job.getStageId())) {
        continue;
      }
      if (job.getStatus() != JobState.PENDING) {
        continue;
      }
      job.queue();
      anyNewlyQueued = true;
      Map<String, Object> jobPayload = buildJobCreatedPayload(occurredAt, tenantId, execution, job);
      outboxRepository.save(
          new OutboxEntity(
              AGGREGATE_JOB,
              job.getId(),
              JobEventTypes.JOB_CREATED,
              jobCreatedTopic,
              execution.getId().toString(),
              jobPayload,
              occurredAt));
    }

    if (anyNewlyQueued && execution.getStatus() == ExecutionState.PENDING) {
      execution.start();
    }

    recordProcessed(eventId);

    if (anyNewlyQueued) {
      log.info(
          "Queued root jobs for execution",
          kv("execution_id", executionId),
          kv("tenant_id", tenantId),
          kv("root_stage_count", rootStageIds.size()));
    }
  }

  private void recordProcessed(UUID eventId) {
    processedEventRepository.save(
        new ProcessedEventEntity(eventId, ExecutionEventTypes.EXECUTION_CREATED, Instant.now()));
  }

  private static Map<String, Object> buildJobCreatedPayload(
      Instant occurredAt, UUID tenantId, ExecutionEntity execution, JobEntity job) {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", eventId.toString());
    m.put("eventType", JobEventTypes.JOB_CREATED);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", AGGREGATE_JOB);
    m.put("aggregateId", job.getId().toString());
    m.put("tenantId", tenantId.toString());
    m.put("executionId", execution.getId().toString());
    m.put("pipelineId", execution.getPipelineId().toString());
    m.put("pipelineVersion", execution.getPipelineVersion());
    m.put("jobId", job.getId().toString());
    m.put("stageId", job.getStageId());
    m.put("stageName", job.getStageName());
    return m;
  }

  private static UUID requireUuid(Map<String, Object> payload, String key) {
    Object v = payload.get(key);
    if (v == null) {
      throw new IllegalArgumentException("Missing required field: " + key);
    }
    return UUID.fromString(v.toString());
  }

  private static List<String> toStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      return list.stream().map(Object::toString).toList();
    }
    throw new IllegalArgumentException(
        "Expected rootStageIds as a JSON array (List); got " + raw.getClass().getName());
  }
}

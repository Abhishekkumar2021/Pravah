package io.pravah.execution.application;

import io.pravah.common.domain.JobState;
import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues PENDING jobs whose dependencies are satisfied (DAG advancement). */
@Service
public class ExecutionJobQueueingService {

  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final String jobCreatedTopic;

  public ExecutionJobQueueingService(
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic) {
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.jobCreatedTopic = jobCreatedTopic;
  }

  /**
   * Queues all PENDING jobs ready to run and appends {@code job.created} outbox rows.
   *
   * <p>Must run within an existing transaction (caller-provided) to ensure atomicity with the
   * surrounding execution/job operations.
   *
   * @return number of jobs newly queued
   */
  @Transactional
  public int queueReadyJobs(
      ExecutionEntity execution, Map<String, Object> definition, Instant occurredAt) {
    List<JobEntity> jobs =
        jobEntityRepository.findByExecutionIdOrderByStageIdAsc(execution.getId());
    Set<String> succeeded =
        jobs.stream()
            .filter(j -> j.getStatus() == JobState.SUCCEEDED)
            .map(JobEntity::getStageId)
            .collect(Collectors.toSet());
    Map<String, JobEntity> byStage =
        jobs.stream().collect(Collectors.toMap(JobEntity::getStageId, j -> j, (a, b) -> a));

    int queued = 0;
    for (String stageId : StagePlanner.stagesReadyToQueueAfterSuccesses(definition, succeeded)) {
      JobEntity pending = byStage.get(stageId);
      if (pending == null || pending.getStatus() != JobState.PENDING) {
        continue;
      }
      pending.queue();
      Map<String, Object> jobPayload =
          buildJobCreatedPayload(occurredAt, execution.getTenantId(), execution, pending);
      outboxRepository.save(
          new OutboxEntity(
              "job",
              pending.getId(),
              JobEventTypes.JOB_CREATED,
              jobCreatedTopic,
              execution.getId().toString(),
              jobPayload,
              occurredAt));
      queued++;
    }
    return queued;
  }

  private static Map<String, Object> buildJobCreatedPayload(
      Instant occurredAt, UUID tenantId, ExecutionEntity execution, JobEntity job) {
    UUID newEventId = UUID.randomUUID();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventId", newEventId.toString());
    m.put("eventType", JobEventTypes.JOB_CREATED);
    m.put("occurredAt", occurredAt.toString());
    m.put("aggregateType", "job");
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
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues PENDING jobs whose dependencies are satisfied (DAG advancement).
 *
 * <p>Supports configurable parallelism limits (US-02.09): at most N stages run concurrently per
 * execution. The cap is resolved from {@code definition.execution.maxParallelStages} falling back
 * to {@code pravah.execution.max-parallel-stages}.
 */
@Service
public class ExecutionJobQueueingService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionJobQueueingService.class);

  private final JobEntityRepository jobEntityRepository;
  private final OutboxRepository outboxRepository;
  private final String jobCreatedTopic;
  private final int defaultMaxParallelStages;

  public ExecutionJobQueueingService(
      JobEntityRepository jobEntityRepository,
      OutboxRepository outboxRepository,
      @Value("${pravah.outbox.topic.job-created}") String jobCreatedTopic,
      @Value("${pravah.execution.max-parallel-stages:4}") int defaultMaxParallelStages) {
    this.jobEntityRepository = jobEntityRepository;
    this.outboxRepository = outboxRepository;
    this.jobCreatedTopic = jobCreatedTopic;
    this.defaultMaxParallelStages = defaultMaxParallelStages;
  }

  /**
   * Queues PENDING jobs ready to run (dependencies satisfied) up to the parallelism cap, and
   * appends {@code job.created} outbox rows.
   *
   * <p>Parallelism is capped per execution: {@code definition.execution.maxParallelStages}
   * overrides the system default. Jobs are queued in declaration order until the cap is reached.
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

    int maxParallel =
        ExecutionParallelismPolicy.resolveMaxParallelStages(definition, defaultMaxParallelStages);
    int activeJobs = ExecutionParallelismPolicy.countActiveJobs(jobs);
    int availableSlots = ExecutionParallelismPolicy.availableSlots(maxParallel, activeJobs);

    if (availableSlots <= 0) {
      log.debug(
          "Execution {} at parallelism cap ({} active, max {}); no jobs queued",
          execution.getId(),
          activeJobs,
          maxParallel);
      return 0;
    }

    Set<String> succeeded =
        jobs.stream()
            .filter(j -> j.getStatus() == JobState.SUCCEEDED)
            .map(JobEntity::getStageId)
            .collect(Collectors.toSet());
    Map<String, JobEntity> byStage =
        jobs.stream().collect(Collectors.toMap(JobEntity::getStageId, j -> j, (a, b) -> a));

    int queued = 0;
    for (String stageId : StagePlanner.stagesReadyToQueueAfterSuccesses(definition, succeeded)) {
      if (queued >= availableSlots) {
        log.debug(
            "Execution {} reached parallelism cap; {} jobs queued this pass, {} remain pending",
            execution.getId(),
            queued,
            StagePlanner.stagesReadyToQueueAfterSuccesses(definition, succeeded).size() - queued);
        break;
      }
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

    if (queued > 0) {
      log.info(
          "Execution {}: queued {} jobs (active={}, max={})",
          execution.getId(),
          queued,
          activeJobs + queued,
          maxParallel);
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

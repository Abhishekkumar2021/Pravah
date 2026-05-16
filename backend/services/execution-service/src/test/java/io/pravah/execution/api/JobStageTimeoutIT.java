package io.pravah.execution.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.domain.JobState;
import io.pravah.execution.application.EmbeddedRunnerIds;
import io.pravah.execution.application.JobFailureService;
import io.pravah.execution.application.JobTimeoutProcessor;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
      "pravah.realtime.redis-enabled=false",
      "pravah.outbox.relay.enabled=false",
      "pravah.kafka.execution-created-listener-enabled=false",
      "pravah.kafka.job-worker-listener-enabled=false",
      "spring.task.scheduling.enabled=false"
    })
class JobStageTimeoutIT extends AbstractExecutionPostgresIT {

  @Autowired private JobTimeoutProcessor jobTimeoutProcessor;
  @Autowired private ExecutionEntityRepository executionEntityRepository;
  @Autowired private JobEntityRepository jobEntityRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void processTimedOutJob_failsRunningJobWithTimeoutExitCode() throws Exception {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    Map<String, Object> definition =
        Map.of(
            "timeout_seconds",
            5,
            "stages",
            List.of(Map.of("id", "slow", "name", "Slow stage", "type", "echo")));

    ExecutionEntity execution =
        executionEntityRepository.save(
            ExecutionEntity.builder()
                .tenantId(tenantId)
                .pipelineId(UUID.randomUUID())
                .pipelineVersion(1)
                .triggerType("manual")
                .definitionSnapshot(definition)
                .build());
    execution.start();
    executionEntityRepository.save(execution);

    JobEntity job =
        jobEntityRepository.save(
            JobEntity.builder()
                .executionId(execution.getId())
                .stageId("slow")
                .stageName("Slow stage")
                .build());
    job.queue();
    job.assign(EmbeddedRunnerIds.LOCAL);
    setStartedAt(job, Instant.now().minusSeconds(30));
    job = jobEntityRepository.saveAndFlush(job);

    jobTimeoutProcessor.processTimedOutJob(job.getId(), Instant.now());

    JobEntity updated = jobEntityRepository.findById(job.getId()).orElseThrow();
    assertThat(updated.getExitCode()).isEqualTo(JobFailureService.EXIT_CODE_TIMEOUT);
    assertThat(updated.getErrorMessage()).contains("timed out");
    assertThat(updated.getStatus()).isIn(JobState.FAILED, JobState.QUEUED);
  }

  private static void setStartedAt(JobEntity job, Instant startedAt) throws Exception {
    Field field = JobEntity.class.getDeclaredField("startedAt");
    field.setAccessible(true);
    field.set(job, startedAt);
  }
}

package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.artifact.ArtifactMetadata;
import io.pravah.execution.infrastructure.artifact.ArtifactStorageService;
import io.pravah.execution.infrastructure.artifact.ArtifactType;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactPublisher")
class ArtifactPublisherTest {

  @Mock private ArtifactStorageService artifactStorageService;
  @Mock private JobLogService jobLogService;

  private ArtifactPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new ArtifactPublisher(artifactStorageService, jobLogService, new ObjectMapper());
  }

  @Test
  void publishJsonIfLarge_uploadsWhenOverThreshold() throws Exception {
    JobEntity job = job();
    ExecutionEntity execution = execution();

    List<Map<String, Object>> rows = List.of(Map.of("id", 1, "data", "x".repeat(200_000)));

    when(artifactStorageService.upload(
            anyString(),
            any(),
            any(),
            eq(ArtifactType.OUTPUT),
            anyString(),
            any(byte[].class),
            anyString()))
        .thenReturn(
            new ArtifactMetadata(
                "tenant/exec/job/result.json.gz",
                "result.json.gz",
                ArtifactType.OUTPUT,
                1024,
                "application/gzip",
                java.time.Instant.now()));

    ArtifactMetadata metadata = publisher.publishJsonIfLarge(job, execution, "result.json", rows);

    assertThat(metadata).isNotNull();
    verify(artifactStorageService)
        .upload(
            anyString(),
            any(),
            any(),
            eq(ArtifactType.OUTPUT),
            anyString(),
            any(byte[].class),
            anyString());
    verify(jobLogService).append(eq(job.getId()), eq(JobLogLevel.INFO), anyString());
  }

  @Test
  void publishJsonIfLarge_returnsNullForSmallPayload() throws Exception {
    JobEntity job = job();
    ExecutionEntity execution = execution();

    ArtifactMetadata metadata =
        publisher.publishJsonIfLarge(job, execution, "small.json", List.of(Map.of("id", 1)));

    assertThat(metadata).isNull();
    verifyNoInteractions(artifactStorageService);
  }

  private static JobEntity job() throws Exception {
    JobEntity job =
        JobEntity.builder().executionId(UUID.randomUUID()).stageId("sql").stageName("SQL").build();
    setId(job, UUID.randomUUID());
    return job;
  }

  private static ExecutionEntity execution() throws Exception {
    ExecutionEntity execution =
        ExecutionEntity.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .triggerType("manual")
            .build();
    setId(execution, UUID.randomUUID());
    return execution;
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field field = entity.getClass().getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}

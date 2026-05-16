package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobLogEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobLogEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobLogServiceTest {

  @Mock private JobLogEntityRepository jobLogEntityRepository;
  @Mock private JobEntityRepository jobEntityRepository;

  @InjectMocks private JobLogService jobLogService;

  @Test
  void append_persistsLogLine() {
    UUID jobId = UUID.randomUUID();
    jobLogService.append(jobId, JobLogLevel.INFO, "hello");

    ArgumentCaptor<JobLogEntity> captor = ArgumentCaptor.forClass(JobLogEntity.class);
    verify(jobLogEntityRepository).save(captor.capture());
    assertThat(captor.getValue().getJobId()).isEqualTo(jobId);
    assertThat(captor.getValue().getLevel()).isEqualTo(JobLogLevel.INFO);
    assertThat(captor.getValue().getMessage()).isEqualTo("hello");
  }

  @Test
  void listLogs_returnsLinesForJobInExecution() {
    UUID executionId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    JobEntity job =
        JobEntity.builder().executionId(executionId).stageId("a").stageName("Build").build();
    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));

    JobLogEntity line =
        new JobLogEntity(
            jobId, Instant.parse("2026-05-16T10:00:00Z"), JobLogLevel.INFO, "ok", null);
    when(jobLogEntityRepository.findByJobIdAndOptionalLevel(
            eq(jobId), eq(null), any(Pageable.class)))
        .thenReturn(List.of(line));

    var response = jobLogService.listLogs(executionId, jobId, null);

    assertThat(response.executionId()).isEqualTo(executionId);
    assertThat(response.lines()).hasSize(1);
    assertThat(response.lines().getFirst().message()).isEqualTo("ok");
  }

  @Test
  void append_blankMessage_throwsValidation() {
    assertThatThrownBy(() -> jobLogService.append(UUID.randomUUID(), JobLogLevel.INFO, "  "))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void listLogs_invalidLevel_throwsValidation() {
    UUID executionId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    JobEntity job =
        JobEntity.builder().executionId(executionId).stageId("a").stageName("Build").build();
    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> jobLogService.listLogs(executionId, jobId, "TRACE"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void listLogs_wrongExecution_throwsNotFound() {
    UUID executionId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    JobEntity job =
        JobEntity.builder().executionId(UUID.randomUUID()).stageId("a").stageName("Build").build();
    when(jobEntityRepository.findById(jobId)).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> jobLogService.listLogs(executionId, jobId, null))
        .isInstanceOf(EntityNotFoundException.class);
  }
}

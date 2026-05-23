package io.pravah.runnerservice.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.runnerservice.domain.JobAssignment;
import io.pravah.runnerservice.repository.JobAssignmentRepository;
import io.pravah.runnerservice.repository.RunnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobAssignmentServiceAuthTest {

  @Mock private RunnerService runnerService;
  @Mock private RunnerConnectionManager connectionManager;
  @Mock private JobAssignmentRepository assignmentRepository;
  @Mock private RunnerRepository runnerRepository;

  @Mock
  private io.pravah.runnerservice.infrastructure.client.ExecutionJobCompletionClient
      completionClient;

  private JobAssignmentService service;

  @BeforeEach
  void setUp() {
    service =
        new JobAssignmentService(
            runnerService,
            connectionManager,
            assignmentRepository,
            runnerRepository,
            completionClient);
  }

  @Test
  void markCompleted_ignoresWrongRunner() {
    UUID jobId = UUID.randomUUID();
    UUID assignedRunner = UUID.randomUUID();
    UUID impostor = UUID.randomUUID();

    JobAssignment assignment = new JobAssignment();
    assignment.setJobId(jobId);
    assignment.setRunnerId(assignedRunner);

    when(assignmentRepository.findByJobId(jobId)).thenReturn(Optional.of(assignment));

    service.markCompleted(jobId, impostor, true, 0, java.util.Map.of());

    verify(assignmentRepository, never()).save(assignment);
    verify(completionClient, never())
        .notifyCompletion(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void markStarted_ignoresWrongRunner() {
    UUID jobId = UUID.randomUUID();
    UUID assignedRunner = UUID.randomUUID();
    UUID impostor = UUID.randomUUID();

    JobAssignment assignment = new JobAssignment();
    assignment.setJobId(jobId);
    assignment.setRunnerId(assignedRunner);
    assignment.setStatus("ASSIGNED");

    when(assignmentRepository.findByJobId(jobId)).thenReturn(Optional.of(assignment));

    service.markStarted(jobId, impostor);

    verify(assignmentRepository, never()).save(assignment);
  }

  @Test
  void isAssignedToRunner_returnsTrueForCorrectRunner() {
    UUID jobId = UUID.randomUUID();
    UUID runnerId = UUID.randomUUID();

    JobAssignment assignment = new JobAssignment();
    assignment.setJobId(jobId);
    assignment.setRunnerId(runnerId);

    when(assignmentRepository.findByJobId(jobId)).thenReturn(Optional.of(assignment));

    org.assertj.core.api.Assertions.assertThat(service.isAssignedToRunner(jobId, runnerId))
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(service.isAssignedToRunner(jobId, UUID.randomUUID()))
        .isFalse();
  }
}

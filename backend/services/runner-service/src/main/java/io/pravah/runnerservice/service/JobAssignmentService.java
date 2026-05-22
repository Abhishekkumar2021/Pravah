package io.pravah.runnerservice.service;

import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.proto.runner.JobSpec;
import io.pravah.proto.runner.ServerMessage;
import io.pravah.runnerservice.domain.JobAssignment;
import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.infrastructure.client.ExecutionJobCompletionClient;
import io.pravah.runnerservice.repository.JobAssignmentRepository;
import io.pravah.runnerservice.repository.RunnerRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assigns execution jobs to runners using label matching and least-loaded selection. */
@Service
@Transactional
public class JobAssignmentService {

  private static final Logger log = LoggerFactory.getLogger(JobAssignmentService.class);

  private final RunnerService runnerService;
  private final RunnerConnectionManager connectionManager;
  private final JobAssignmentRepository assignmentRepository;
  private final RunnerRepository runnerRepository;
  private final ExecutionJobCompletionClient executionJobCompletionClient;

  public JobAssignmentService(
      RunnerService runnerService,
      RunnerConnectionManager connectionManager,
      JobAssignmentRepository assignmentRepository,
      RunnerRepository runnerRepository,
      ExecutionJobCompletionClient executionJobCompletionClient) {
    this.runnerService = runnerService;
    this.connectionManager = connectionManager;
    this.assignmentRepository = assignmentRepository;
    this.runnerRepository = runnerRepository;
    this.executionJobCompletionClient = executionJobCompletionClient;
  }

  /**
   * Selects the best available runner and assigns the job.
   *
   * @return assignment record, or empty if no runner available
   */
  public Optional<JobAssignment> assignJob(
      UUID tenantId,
      UUID jobId,
      UUID executionId,
      UUID pipelineId,
      String jobName,
      RemoteJobSpecPayload spec,
      Map<String, String> requiredLabels) {

    if (assignmentRepository.findByJobId(jobId).isPresent()) {
      return assignmentRepository.findByJobId(jobId);
    }

    List<Runner> candidates = runnerService.findAvailableRunners(tenantId);
    Optional<Runner> selected = selectRunner(candidates, spec.executor(), requiredLabels);

    if (selected.isEmpty()) {
      log.warn("No runner available for job {}", jobId);
      return Optional.empty();
    }

    Runner runner = selected.get();
    if (!runnerService.assignJob(runner.getId())) {
      return Optional.empty();
    }

    JobAssignment assignment = new JobAssignment();
    assignment.setRunnerId(runner.getId());
    assignment.setJobId(jobId);
    assignment.setExecutionId(executionId);
    assignment.setStatus("ASSIGNED");
    assignment.setAssignedAt(Instant.now());
    assignment = assignmentRepository.save(assignment);

    if (!dispatchToRunner(runner.getId(), jobId, executionId, pipelineId, jobName, spec)) {
      assignmentRepository.delete(assignment);
      runnerService.completeJob(runner.getId());
      log.warn("Failed to dispatch job {} to runner {}", jobId, runner.getId());
      return Optional.empty();
    }
    log.info("Assigned job {} to runner {}", jobId, runner.getId());
    return Optional.of(assignment);
  }

  public void markStarted(UUID jobId) {
    assignmentRepository
        .findByJobId(jobId)
        .ifPresent(
            a -> {
              a.setStatus("RUNNING");
              a.setStartedAt(Instant.now());
              assignmentRepository.save(a);
            });
  }

  public void markCompleted(UUID jobId, boolean success, int exitCode) {
    markCompleted(jobId, success, exitCode, Map.of());
  }

  public void markCompleted(UUID jobId, boolean success, int exitCode, Map<String, Object> output) {
    assignmentRepository
        .findByJobId(jobId)
        .ifPresent(
            a -> {
              a.setStatus(success ? "COMPLETED" : "FAILED");
              a.setCompletedAt(Instant.now());
              assignmentRepository.save(a);
              runnerService.completeJob(a.getRunnerId());
              runnerRepository
                  .findById(a.getRunnerId())
                  .ifPresent(
                      runner ->
                          executionJobCompletionClient.notifyCompletion(
                              runner.getTenantId(),
                              jobId,
                              a.getRunnerId(),
                              exitCode,
                              output != null ? output : Map.of()));
            });
  }

  private Optional<Runner> selectRunner(
      List<Runner> candidates, String stageType, Map<String, String> requiredLabels) {

    return candidates.stream()
        .filter(r -> connectionManager.isConnected(r.getId()))
        .filter(r -> supportsExecutor(r, stageType))
        .filter(r -> matchesLabels(r, requiredLabels))
        .min(Comparator.comparingInt(Runner::getActiveJobs));
  }

  private boolean supportsExecutor(Runner runner, String stageType) {
    if (stageType == null || stageType.isBlank()) {
      return true;
    }
    String executors = runner.getSupportedExecutors();
    if (executors == null || executors.isBlank()) {
      return true;
    }
    return Arrays.stream(executors.split(","))
        .map(String::trim)
        .map(String::toLowerCase)
        .anyMatch(e -> e.equals(stageType.toLowerCase()));
  }

  private boolean matchesLabels(Runner runner, Map<String, String> requiredLabels) {
    if (requiredLabels == null || requiredLabels.isEmpty()) {
      return true;
    }
    Map<String, String> runnerLabels = runner.getLabels();
    if (runnerLabels == null) {
      return false;
    }
    for (Map.Entry<String, String> entry : requiredLabels.entrySet()) {
      String actual = runnerLabels.get(entry.getKey());
      if (actual == null || !actual.equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private boolean dispatchToRunner(
      UUID runnerId,
      UUID jobId,
      UUID executionId,
      UUID pipelineId,
      String jobName,
      RemoteJobSpecPayload spec) {
    JobSpec jobSpec = JobSpecMapper.toProto(spec);
    io.pravah.proto.runner.JobAssignment.Builder assignmentBuilder =
        io.pravah.proto.runner.JobAssignment.newBuilder()
            .setJobId(jobId.toString())
            .setRunId(executionId.toString())
            .setJobName(jobName != null && !jobName.isBlank() ? jobName : "job-" + jobId)
            .setSpec(jobSpec);
    if (pipelineId != null) {
      assignmentBuilder.setPipelineId(pipelineId.toString());
    }
    ServerMessage message =
        ServerMessage.newBuilder().setJobAssignment(assignmentBuilder.build()).build();
    return connectionManager.sendMessage(runnerId, message);
  }
}

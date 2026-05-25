package io.pravah.execution.api;

import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.api.dto.GetExecutionResponse;
import io.pravah.execution.api.dto.JobLogsResponse;
import io.pravah.execution.api.dto.ListExecutionsResponse;
import io.pravah.execution.api.dto.RetryExecutionRequest;
import io.pravah.execution.application.ExecutionApplicationService;
import io.pravah.execution.application.JobLogService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

  private final ExecutionApplicationService executionApplicationService;
  private final JobLogService jobLogService;

  public ExecutionController(
      ExecutionApplicationService executionApplicationService, JobLogService jobLogService) {
    this.executionApplicationService = executionApplicationService;
    this.jobLogService = jobLogService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:write', 'executions:*', 'pipelines:write', 'pipelines:*')")
  public CreateExecutionResponse start(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody CreateExecutionRequest request) {
    return executionApplicationService.startManualExecution(request, authorization);
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:write', 'executions:*', 'executions:cancel', 'pipelines:write', 'pipelines:*')")
  public GetExecutionResponse cancel(@PathVariable("id") UUID id) {
    return executionApplicationService.cancelExecution(id);
  }

  /** Retry from a failed stage (US-02.05). Creates a new execution linked via {@code retry_of}. */
  @PostMapping("/{id}/retry")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:write', 'executions:*', 'pipelines:write', 'pipelines:*')")
  public CreateExecutionResponse retry(
      @PathVariable("id") UUID id, @Valid @RequestBody RetryExecutionRequest request) {
    return executionApplicationService.retryFromStage(id, request);
  }

  /** Clears persisted checkpoints for an execution (US-02.12). */
  @DeleteMapping("/{id}/checkpoints")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:write', 'executions:*', 'pipelines:write', 'pipelines:*')")
  public void clearCheckpoints(@PathVariable UUID id) {
    executionApplicationService.clearCheckpoints(id);
  }

  @GetMapping
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:read', 'executions:*', 'pipelines:read', 'pipelines:*')")
  public ListExecutionsResponse list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID pipelineId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("size must be between 1 and 100");
    }
    return executionApplicationService.listExecutions(status, pipelineId, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:read', 'executions:*', 'pipelines:read', 'pipelines:*')")
  public GetExecutionResponse get(@PathVariable UUID id) {
    return executionApplicationService.getExecution(id);
  }

  /** Per-job logs for run detail (US-02.03). */
  @GetMapping("/{executionId}/jobs/{jobId}/logs")
  @PreAuthorize(
      "@permissionChecker.hasAny('executions:read', 'executions:*', 'pipelines:read', 'pipelines:*')")
  public JobLogsResponse jobLogs(
      @PathVariable UUID executionId,
      @PathVariable UUID jobId,
      @RequestParam(required = false) String level) {
    return jobLogService.listLogs(executionId, jobId, level);
  }
}

package io.pravah.execution.api;

import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.api.dto.TriggerPipelineRunRequest;
import io.pravah.execution.api.dto.TriggerPipelineRunResponse;
import io.pravah.execution.application.ExecutionApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API event trigger for pipeline runs (US-03.08).
 *
 * <p>Product language uses "workflows"; Pravah REST uses {@code pipelines}.
 */
@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineRunController {

  private final ExecutionApplicationService executionApplicationService;

  public PipelineRunController(ExecutionApplicationService executionApplicationService) {
    this.executionApplicationService = executionApplicationService;
  }

  /**
   * Triggers a pipeline run via API (US-03.08).
   *
   * @param pipelineId the pipeline UUID (from path)
   * @param authorization JWT or API token for pipeline resolution
   * @param request optional body with version, parameters, and async flag
   * @return full execution details (201) or minimal id-only response (202 if async)
   */
  @PostMapping("/{pipelineId}/runs")
  public ResponseEntity<Object> trigger(
      @PathVariable UUID pipelineId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody(required = false) TriggerPipelineRunRequest request) {
    TriggerPipelineRunRequest body =
        request != null ? request : new TriggerPipelineRunRequest(null, null, null);
    boolean async = Boolean.TRUE.equals(body.async());
    if (async) {
      UUID runId =
          executionApplicationService.startApiExecution(pipelineId, body, authorization).id();
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerPipelineRunResponse(runId));
    }
    CreateExecutionResponse created =
        executionApplicationService.startApiExecution(pipelineId, body, authorization);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}

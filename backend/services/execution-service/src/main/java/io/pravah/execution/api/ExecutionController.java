package io.pravah.execution.api;

import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.api.dto.GetExecutionResponse;
import io.pravah.execution.api.dto.ListExecutionsResponse;
import io.pravah.execution.application.ExecutionApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

  public ExecutionController(ExecutionApplicationService executionApplicationService) {
    this.executionApplicationService = executionApplicationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateExecutionResponse start(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody CreateExecutionRequest request) {
    return executionApplicationService.startManualExecution(request, authorization);
  }

  @PostMapping("/{id}/cancel")
  public GetExecutionResponse cancel(@PathVariable("id") UUID id) {
    return executionApplicationService.cancelExecution(id);
  }

  @GetMapping
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
  public GetExecutionResponse get(@PathVariable UUID id) {
    return executionApplicationService.getExecution(id);
  }
}

package io.pravah.execution.api;

import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.api.dto.CreateExecutionResponse;
import io.pravah.execution.application.ExecutionApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

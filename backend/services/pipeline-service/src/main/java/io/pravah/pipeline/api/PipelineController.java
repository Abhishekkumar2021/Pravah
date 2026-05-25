package io.pravah.pipeline.api;

import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.PipelineDetailResponse;
import io.pravah.pipeline.api.dto.PipelineListResponse;
import io.pravah.pipeline.api.dto.PipelineResponse;
import io.pravah.pipeline.api.dto.PipelineVersionDefinitionResponse;
import io.pravah.pipeline.api.dto.PublishPipelineRequest;
import io.pravah.pipeline.api.dto.UpdatePipelineRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineResponse;
import io.pravah.pipeline.application.PipelineApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

  private final PipelineApplicationService pipelineApplicationService;

  public PipelineController(PipelineApplicationService pipelineApplicationService) {
    this.pipelineApplicationService = pipelineApplicationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineResponse create(@Valid @RequestBody CreatePipelineRequest request) {
    return pipelineApplicationService.createPipeline(request);
  }

  @PostMapping("/validate")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:write', 'pipelines:*')")
  public ValidatePipelineResponse validate(@Valid @RequestBody ValidatePipelineRequest request) {
    return pipelineApplicationService.validatePipelineDefinition(request);
  }

  @GetMapping("/{id}/versions/{version}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public PipelineVersionDefinitionResponse getPublishedVersion(
      @PathVariable UUID id, @PathVariable int version) {
    return pipelineApplicationService.getPublishedVersionDefinition(id, version);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public PipelineDetailResponse get(@PathVariable UUID id) {
    return pipelineApplicationService.getPipeline(id);
  }

  private static final int MAX_PAGE_SIZE = 100;

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('pipelines:read', 'pipelines:*')")
  public PipelineListResponse list(
      @RequestParam UUID projectId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    return pipelineApplicationService.listPipelines(projectId, status, safePage, safeSize);
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdatePipelineRequest request) {
    return pipelineApplicationService.updatePipeline(id, request);
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineResponse publish(
      @PathVariable UUID id, @Valid @RequestBody PublishPipelineRequest request) {
    return pipelineApplicationService.publishPipeline(id, request.definitionYaml());
  }

  @PostMapping("/{id}/archive")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineResponse archive(@PathVariable UUID id) {
    return pipelineApplicationService.archivePipeline(id);
  }

  @PostMapping("/{id}/restore")
  @PreAuthorize("@permissionChecker.hasAny('pipelines:write', 'pipelines:*')")
  public PipelineResponse restore(@PathVariable UUID id) {
    return pipelineApplicationService.restorePipeline(id);
  }
}

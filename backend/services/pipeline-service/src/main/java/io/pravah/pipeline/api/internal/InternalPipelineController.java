package io.pravah.pipeline.api.internal;

import io.pravah.pipeline.application.PipelineApplicationService;
import io.pravah.pipeline.application.PublishedPipelineSnapshot;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal API for service-to-service published pipeline resolution. */
@RestController
@RequestMapping("/api/v1/internal/pipelines")
public class InternalPipelineController {

  private final PipelineApplicationService pipelineApplicationService;

  public InternalPipelineController(PipelineApplicationService pipelineApplicationService) {
    this.pipelineApplicationService = pipelineApplicationService;
  }

  @GetMapping("/{pipelineId}/published")
  public PublishedPipelineSnapshot getPublished(@PathVariable UUID pipelineId) {
    return pipelineApplicationService.resolvePublishedForExecution(pipelineId);
  }
}

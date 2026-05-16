package io.pravah.execution.infrastructure.pipeline;

import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.spring.security.InternalServiceAuthFilter;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Resolves published pipelines via pipeline-service internal API (scheduled runs). */
@Component
public class InternalHttpPipelineCatalog {

  private final RestClient restClient;
  private final String internalSecret;

  public InternalHttpPipelineCatalog(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.pipeline-service.base-url}") String pipelineBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(pipelineBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  public PublishedPipelineSnapshot resolvePublished(UUID tenantId, UUID pipelineId) {
    InternalPipelineResponse body =
        restClient
            .get()
            .uri("/api/v1/internal/pipelines/{id}/published", pipelineId)
            .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
            .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
            .retrieve()
            .body(InternalPipelineResponse.class);
    if (body == null) {
      throw new IllegalStateException("Pipeline service returned empty published snapshot");
    }
    return new PublishedPipelineSnapshot(
        body.pipelineId(), body.pipelineVersion(), body.definition(), body.status());
  }

  public record InternalPipelineResponse(
      UUID pipelineId, int pipelineVersion, Map<String, Object> definition, String status) {}
}

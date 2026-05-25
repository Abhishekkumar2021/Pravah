package io.pravah.execution.infrastructure.pipeline;

import io.pravah.execution.application.port.SecretCatalog;
import io.pravah.spring.security.InternalServiceAuthFilter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class InternalHttpSecretCatalog implements SecretCatalog {

  private final RestClient restClient;
  private final String internalSecret;

  public InternalHttpSecretCatalog(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.pipeline-service.base-url}") String pipelineBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(pipelineBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  @Override
  public String resolve(UUID tenantId, UUID executionId, Instant executionTime, String secretName) {
    String uri =
        UriComponentsBuilder.fromPath("/api/v1/internal/secrets/{name}")
            .queryParam("executionId", executionId)
            .queryParam("executionTime", executionTime)
            .buildAndExpand(secretName)
            .toUriString();

    InternalSecretResponse body =
        restClient
            .get()
            .uri(uri)
            .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
            .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
            .retrieve()
            .body(InternalSecretResponse.class);

    if (body == null || body.value() == null) {
      throw new IllegalStateException(
          "Pipeline service returned empty secret resolution for: " + secretName);
    }
    return body.value();
  }

  public record InternalSecretResponse(String name, String value) {}
}

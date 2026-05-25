package io.pravah.execution.infrastructure.pipeline;

import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.spring.security.InternalServiceAuthFilter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InternalHttpConnectionCatalog implements ConnectionCatalog {

  private final RestClient restClient;
  private final String internalSecret;

  public InternalHttpConnectionCatalog(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.pipeline-service.base-url}") String pipelineBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(pipelineBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  @Override
  public ResolvedJdbcConnection resolve(UUID tenantId, String connectionName) {
    InternalConnectionResponse body =
        restClient
            .get()
            .uri("/api/v1/internal/connections/{name}", connectionName)
            .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
            .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
            .retrieve()
            .body(InternalConnectionResponse.class);
    if (body == null) {
      throw new IllegalStateException("Pipeline service returned empty connection resolution");
    }
    return new ResolvedJdbcConnection(
        body.name(), body.jdbcUrl(), body.username(), body.password());
  }

  public record InternalConnectionResponse(
      String name, String type, String jdbcUrl, String username, String password) {}
}

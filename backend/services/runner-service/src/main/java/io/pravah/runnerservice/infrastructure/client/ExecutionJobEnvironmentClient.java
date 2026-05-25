package io.pravah.runnerservice.infrastructure.client;

import io.pravah.spring.security.InternalServiceAuthFilter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Proxies job environment secret resolution to execution-service. */
@Component
public class ExecutionJobEnvironmentClient {

  private static final Logger log = LoggerFactory.getLogger(ExecutionJobEnvironmentClient.class);

  private final RestClient restClient;
  private final String internalSecret;

  public ExecutionJobEnvironmentClient(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.execution-service.base-url}") String executionBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(executionBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  public Map<String, String> resolve(
      UUID tenantId, UUID executionId, UUID jobId, Map<String, String> secretEnvironment) {
    try {
      ResolveResponse response =
          restClient
              .post()
              .uri(
                  "/api/v1/internal/executions/{executionId}/jobs/{jobId}/environment-secrets",
                  executionId,
                  jobId)
              .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
              .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
              .body(new ResolveRequest(secretEnvironment))
              .retrieve()
              .body(ResolveResponse.class);
      return response != null && response.values() != null ? response.values() : Map.of();
    } catch (Exception e) {
      log.warn(
          "Failed to resolve job environment secrets: executionId={}, jobId={}",
          executionId,
          jobId,
          e);
      throw e;
    }
  }

  record ResolveRequest(Map<String, String> secretEnvironment) {}

  record ResolveResponse(Map<String, String> values) {}
}

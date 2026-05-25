package io.pravah.runnerservice.infrastructure.client;

import io.pravah.spring.security.InternalServiceAuthFilter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Notifies execution-service when a remote runner finishes a job. */
@Component
public class ExecutionJobCompletionClient {

  private static final Logger log = LoggerFactory.getLogger(ExecutionJobCompletionClient.class);

  private final RestClient restClient;
  private final String internalSecret;

  public ExecutionJobCompletionClient(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.execution-service.base-url}") String executionBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(executionBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  public void notifyCompletion(
      UUID tenantId, UUID jobId, UUID runnerId, int exitCode, Map<String, Object> output) {
    try {
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("source", "runner");
      if (output != null) {
        payload.putAll(output);
      }
      restClient
          .post()
          .uri("/api/v1/internal/jobs/{jobId}/complete", jobId)
          .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
          .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
          .body(new CompletionRequest(exitCode, runnerId, payload))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn(
          "Failed to notify execution-service of job completion: jobId={}, status={}",
          jobId,
          e.getStatusCode().value());
    }
  }

  record CompletionRequest(int exitCode, UUID runnerId, Map<String, Object> output) {}
}

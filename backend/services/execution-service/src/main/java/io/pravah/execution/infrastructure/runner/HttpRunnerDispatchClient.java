package io.pravah.execution.infrastructure.runner;

import io.pravah.execution.application.port.RunnerDispatchPort;
import io.pravah.spring.security.InternalServiceAuthFilter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(
    name = "pravah.runner-service.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HttpRunnerDispatchClient implements RunnerDispatchPort {

  private static final Logger log = LoggerFactory.getLogger(HttpRunnerDispatchClient.class);

  private final RestClient restClient;
  private final String internalSecret;

  public HttpRunnerDispatchClient(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.runner-service.base-url}") String runnerBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(runnerBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  @Override
  public Optional<UUID> dispatch(
      UUID tenantId, UUID jobId, UUID executionId, String stageType, Map<String, String> labels) {
    try {
      AssignmentResponse response =
          restClient
              .post()
              .uri("/api/v1/internal/runners/assignments")
              .header(InternalServiceAuthFilter.SECRET_HEADER, internalSecret)
              .header(InternalServiceAuthFilter.TENANT_HEADER, tenantId.toString())
              .body(new AssignJobRequest(jobId, executionId, stageType, labels))
              .retrieve()
              .body(AssignmentResponse.class);
      if (response == null || response.runnerId() == null) {
        return Optional.empty();
      }
      return Optional.of(response.runnerId());
    } catch (RestClientResponseException e) {
      log.warn("Runner dispatch failed: status={}, jobId={}", e.getStatusCode().value(), jobId);
      return Optional.empty();
    }
  }

  record AssignJobRequest(
      UUID jobId, UUID executionId, String stageType, Map<String, String> labels) {}

  record AssignmentResponse(
      UUID assignmentId, UUID runnerId, UUID jobId, UUID executionId, String status) {}
}

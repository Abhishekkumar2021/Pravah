package io.pravah.scheduler.infrastructure.client;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.pravah.scheduler.infrastructure.security.InternalServiceHeaders;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Triggers pipeline runs via execution-service internal API.
 *
 * <p>Uses Resilience4j circuit breaker and retry patterns (ADR-012, LLD-01) to handle transient
 * failures and prevent cascading failures when execution-service is unavailable.
 */
@Component
public class ExecutionTriggerClient {

  private static final Logger log = LoggerFactory.getLogger(ExecutionTriggerClient.class);
  private static final String CIRCUIT_BREAKER_NAME = "execution-service";

  private final RestClient restClient;
  private final String internalSecret;

  public ExecutionTriggerClient(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.execution-service.base-url}") String executionBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(executionBaseUrl).build();
    this.internalSecret = internalSecret;
  }

  @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "triggerScheduledFallback")
  @Retry(name = CIRCUIT_BREAKER_NAME)
  public UUID triggerScheduledExecution(UUID tenantId, UUID pipelineId, UUID scheduleId) {
    try {
      ScheduledExecutionResponse response =
          restClient
              .post()
              .uri("/api/v1/internal/executions/scheduled")
              .header(InternalServiceHeaders.SECRET_HEADER, internalSecret)
              .header(InternalServiceHeaders.TENANT_HEADER, tenantId.toString())
              .body(new ScheduledExecutionRequest(pipelineId, scheduleId))
              .retrieve()
              .body(ScheduledExecutionResponse.class);
      if (response == null || response.executionId() == null) {
        throw new IllegalStateException("Execution service returned empty response");
      }
      return response.executionId();
    } catch (RestClientResponseException e) {
      log.warn(
          "Scheduled execution trigger failed",
          kv("tenant_id", tenantId),
          kv("pipeline_id", pipelineId),
          kv("schedule_id", scheduleId),
          kv("status", e.getStatusCode().value()),
          kv("body", e.getResponseBodyAsString()));
      throw e;
    }
  }

  @SuppressWarnings("unused")
  private UUID triggerScheduledFallback(
      UUID tenantId, UUID pipelineId, UUID scheduleId, Throwable t) {
    log.error(
        "Circuit breaker fallback triggered for execution-service (scheduled)",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("schedule_id", scheduleId),
        kv("error", t.getMessage()));
    throw new ServiceUnavailableException(
        "Execution service is temporarily unavailable. Scheduled run will be retried.");
  }

  public UUID triggerEventExecution(
      UUID tenantId,
      UUID pipelineId,
      String triggerType,
      UUID triggerId,
      Map<String, Object> parameters) {
    return triggerEventExecution(tenantId, pipelineId, triggerType, triggerId, parameters, null);
  }

  @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "triggerEventFallback")
  @Retry(name = CIRCUIT_BREAKER_NAME)
  public UUID triggerEventExecution(
      UUID tenantId,
      UUID pipelineId,
      String triggerType,
      UUID triggerId,
      Map<String, Object> parameters,
      String idempotencyKey) {
    try {
      EventExecutionResponse response =
          restClient
              .post()
              .uri("/api/v1/internal/executions/event")
              .header(InternalServiceHeaders.SECRET_HEADER, internalSecret)
              .header(InternalServiceHeaders.TENANT_HEADER, tenantId.toString())
              .body(
                  new EventExecutionRequest(
                      pipelineId, triggerType, triggerId, parameters, idempotencyKey))
              .retrieve()
              .body(EventExecutionResponse.class);
      if (response == null || response.executionId() == null) {
        throw new IllegalStateException("Execution service returned empty response");
      }
      return response.executionId();
    } catch (RestClientResponseException e) {
      log.warn(
          "Event execution trigger failed",
          kv("tenant_id", tenantId),
          kv("pipeline_id", pipelineId),
          kv("trigger_type", triggerType),
          kv("trigger_id", triggerId),
          kv("status", e.getStatusCode().value()),
          kv("body", e.getResponseBodyAsString()));
      throw e;
    }
  }

  @SuppressWarnings("unused")
  private UUID triggerEventFallback(
      UUID tenantId,
      UUID pipelineId,
      String triggerType,
      UUID triggerId,
      Map<String, Object> parameters,
      String idempotencyKey,
      Throwable t) {
    log.error(
        "Circuit breaker fallback triggered for execution-service (event)",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("trigger_type", triggerType),
        kv("trigger_id", triggerId),
        kv("error", t.getMessage()));
    throw new ServiceUnavailableException(
        "Execution service is temporarily unavailable. Event trigger will be retried.");
  }

  public record ScheduledExecutionRequest(UUID pipelineId, UUID scheduleId) {}

  public record ScheduledExecutionResponse(UUID executionId) {}

  public record EventExecutionRequest(
      UUID pipelineId,
      String triggerType,
      UUID triggerId,
      Map<String, Object> parameters,
      String idempotencyKey) {}

  public record EventExecutionResponse(UUID executionId) {}
}

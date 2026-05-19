package io.pravah.scheduler.infrastructure.client;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.scheduler.infrastructure.security.InternalServiceHeaders;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Triggers scheduled pipeline runs via execution-service internal API. */
@Component
public class ExecutionTriggerClient {

  private static final Logger log = LoggerFactory.getLogger(ExecutionTriggerClient.class);

  private final RestClient restClient;
  private final String internalSecret;

  public ExecutionTriggerClient(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.execution-service.base-url}") String executionBaseUrl,
      @Value("${pravah.internal-service.secret}") String internalSecret) {
    this.restClient = restClientBuilder.baseUrl(executionBaseUrl).build();
    this.internalSecret = internalSecret;
  }

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

  public UUID triggerEventExecution(
      UUID tenantId,
      UUID pipelineId,
      String triggerType,
      UUID triggerId,
      Map<String, Object> parameters) {
    try {
      EventExecutionResponse response =
          restClient
              .post()
              .uri("/api/v1/internal/executions/event")
              .header(InternalServiceHeaders.SECRET_HEADER, internalSecret)
              .header(InternalServiceHeaders.TENANT_HEADER, tenantId.toString())
              .body(new EventExecutionRequest(pipelineId, triggerType, triggerId, parameters))
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

  public record ScheduledExecutionRequest(UUID pipelineId, UUID scheduleId) {}

  public record ScheduledExecutionResponse(UUID executionId) {}

  public record EventExecutionRequest(
      UUID pipelineId, String triggerType, UUID triggerId, Map<String, Object> parameters) {}

  public record EventExecutionResponse(UUID executionId) {}
}

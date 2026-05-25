package io.pravah.execution.infrastructure.pipeline;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP-based pipeline catalog that fetches pipeline definitions from pipeline-service.
 *
 * <p>Uses Resilience4j circuit breaker and retry patterns (ADR-012, LLD-01) to handle transient
 * failures and prevent cascading failures when pipeline-service is unavailable.
 */
@Component
public class HttpPipelineCatalog implements PipelineCatalog {

  private static final Logger log = LoggerFactory.getLogger(HttpPipelineCatalog.class);
  private static final String CIRCUIT_BREAKER_NAME = "pipeline-service";

  private final RestClient restClient;

  public HttpPipelineCatalog(
      RestClient.Builder restClientBuilder,
      @Value("${pravah.pipeline-service.base-url}") String pipelineBaseUrl) {
    this.restClient = restClientBuilder.baseUrl(pipelineBaseUrl).build();
  }

  @Override
  @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "resolveFallback")
  @Retry(name = CIRCUIT_BREAKER_NAME)
  public PublishedPipelineSnapshot resolve(
      UUID pipelineId, Integer pipelineVersionOrNull, String authorizationHeader) {
    PipelineSummaryJson summary =
        restClient
            .get()
            .uri("/api/v1/pipelines/{id}", pipelineId)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
            .retrieve()
            .body(PipelineSummaryJson.class);
    if (summary == null) {
      throw new EntityNotFoundException("Pipeline", pipelineId);
    }

    int version = pipelineVersionOrNull != null ? pipelineVersionOrNull : summary.currentVersion();

    PipelineVersionJson versionBody;
    try {
      versionBody =
          restClient
              .get()
              .uri("/api/v1/pipelines/{id}/versions/{version}", pipelineId, version)
              .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
              .retrieve()
              .body(PipelineVersionJson.class);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        log.debug(
            "Pipeline version not found from pipeline-service",
            kv("pipeline_id", pipelineId),
            kv("version", version));
        throw new EntityNotFoundException("PipelineVersion", pipelineId + ":v" + version);
      }
      log.warn(
          "Pipeline-service version fetch failed",
          kv("pipeline_id", pipelineId),
          kv("version", version),
          kv("status", e.getStatusCode().value()));
      throw e;
    }
    if (versionBody == null) {
      throw new EntityNotFoundException("PipelineVersion", pipelineId + ":v" + version);
    }

    return new PublishedPipelineSnapshot(
        pipelineId, versionBody.version(), versionBody.definition(), summary.status());
  }

  @SuppressWarnings("unused")
  private PublishedPipelineSnapshot resolveFallback(
      UUID pipelineId, Integer pipelineVersionOrNull, String authorizationHeader, Throwable t) {
    log.error(
        "Circuit breaker fallback triggered for pipeline-service",
        kv("pipeline_id", pipelineId),
        kv("error", t.getMessage()));
    throw new ServiceUnavailableException(
        "Pipeline service is temporarily unavailable. Please try again later.");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PipelineSummaryJson(
      UUID id,
      UUID projectId,
      String name,
      String description,
      int currentVersion,
      String status,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PipelineVersionJson(
      UUID pipelineId,
      int version,
      Map<String, Object> definition,
      java.time.Instant publishedAt,
      UUID publishedBy) {}
}

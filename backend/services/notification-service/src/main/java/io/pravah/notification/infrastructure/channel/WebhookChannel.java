package io.pravah.notification.infrastructure.channel;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.net.UrlSafetyValidator;
import io.pravah.notification.application.dto.AlertContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Generic webhook notification channel. */
@Component
public class WebhookChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(WebhookChannel.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_RETRIES = 2;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public WebhookChannel(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    this.objectMapper = objectMapper;
  }

  @Override
  public String type() {
    return "webhook";
  }

  @Override
  @SuppressWarnings("unchecked")
  public DeliveryResult send(AlertContext context, Map<String, Object> channelConfig) {
    String url = (String) channelConfig.get("url");
    if (url == null || url.isBlank()) {
      return DeliveryResult.failure("webhook", "No webhook URL configured");
    }
    try {
      UrlSafetyValidator.validateHttpUrlForOutboundRequest(url);
    } catch (IllegalArgumentException e) {
      return DeliveryResult.failure("webhook", e.getMessage());
    }

    Map<String, String> headers =
        (Map<String, String>) channelConfig.getOrDefault("headers", Map.of());

    try {
      String payload = buildPayload(context);

      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload));

      // Add custom headers
      headers.forEach(requestBuilder::header);

      HttpRequest request = requestBuilder.build();

      // Retry logic
      HttpResponse<String> response = null;
      Exception lastError = null;

      for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        try {
          response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info(
                "Webhook alert sent",
                kv("event_type", context.eventType()),
                kv("pipeline_id", context.pipelineId()),
                kv("url", maskUrl(url)));
            return DeliveryResult.success("webhook");
          }
          // Non-2xx, might retry on 5xx
          if (response.statusCode() < 500) {
            break; // Don't retry on 4xx
          }
        } catch (Exception e) {
          lastError = e;
          log.debug("Webhook attempt {} failed", attempt + 1, e);
        }

        if (attempt < MAX_RETRIES) {
          Thread.sleep(1000L * (attempt + 1)); // Backoff
        }
      }

      if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
        return DeliveryResult.success("webhook");
      }

      String error =
          response != null
              ? "HTTP " + response.statusCode() + ": " + truncate(response.body(), 200)
              : (lastError != null ? lastError.getMessage() : "Unknown error");

      log.warn(
          "Webhook alert failed after retries",
          kv("event_type", context.eventType()),
          kv("url", maskUrl(url)),
          kv("error", error));

      return DeliveryResult.failure("webhook", error);

    } catch (Exception e) {
      log.error(
          "Failed to send webhook alert",
          kv("event_type", context.eventType()),
          kv("pipeline_id", context.pipelineId()),
          e);
      return DeliveryResult.failure("webhook", e.getMessage());
    }
  }

  private String buildPayload(AlertContext context) throws Exception {
    Map<String, Object> payload = new HashMap<>();
    payload.put("event_type", context.eventType());
    payload.put("tenant_id", context.tenantId() != null ? context.tenantId().toString() : null);
    payload.put(
        "execution_id", context.executionId() != null ? context.executionId().toString() : null);
    payload.put(
        "pipeline_id", context.pipelineId() != null ? context.pipelineId().toString() : null);
    payload.put("pipeline_name", context.pipelineName());
    payload.put("status", context.status());
    payload.put("error_message", context.errorMessage());
    payload.put("stage_name", context.stageName());
    payload.put(
        "occurred_at", context.occurredAt() != null ? context.occurredAt().toString() : null);
    payload.put("environment", context.environment());
    payload.put("execution_url", context.executionUrl());
    payload.put("metadata", context.metadata());

    return objectMapper.writeValueAsString(payload);
  }

  private String maskUrl(String url) {
    // Hide potential tokens in URL
    return url.replaceAll("(token|key|secret)=[^&]+", "$1=***");
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }
}

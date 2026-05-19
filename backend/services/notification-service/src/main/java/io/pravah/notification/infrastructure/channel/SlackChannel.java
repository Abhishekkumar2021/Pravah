package io.pravah.notification.infrastructure.channel;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.notification.application.dto.AlertContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Slack notification channel using incoming webhooks. */
@Component
public class SlackChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(SlackChannel.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public SlackChannel(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    this.objectMapper = objectMapper;
  }

  @Override
  public String type() {
    return "slack";
  }

  @Override
  public DeliveryResult send(AlertContext context, Map<String, Object> channelConfig) {
    String webhookUrl = (String) channelConfig.get("webhookUrl");
    if (webhookUrl == null || webhookUrl.isBlank()) {
      return DeliveryResult.failure("slack", "No webhook URL configured");
    }

    try {
      String payload = buildSlackPayload(context);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(webhookUrl))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        log.info(
            "Slack alert sent",
            kv("event_type", context.eventType()),
            kv("pipeline_id", context.pipelineId()));
        return DeliveryResult.success("slack");
      } else {
        String error = "HTTP " + response.statusCode() + ": " + response.body();
        log.warn(
            "Slack alert failed",
            kv("event_type", context.eventType()),
            kv("status_code", response.statusCode()));
        return DeliveryResult.failure("slack", error);
      }

    } catch (Exception e) {
      log.error(
          "Failed to send Slack alert",
          kv("event_type", context.eventType()),
          kv("pipeline_id", context.pipelineId()),
          e);
      return DeliveryResult.failure("slack", e.getMessage());
    }
  }

  private String buildSlackPayload(AlertContext context) throws Exception {
    Map<String, Object> payload = new HashMap<>();

    String color = getColorForEvent(context.eventType());
    String emoji = getEmojiForEvent(context.eventType());

    // Attachment-based message for rich formatting
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("color", color);
    attachment.put("fallback", context.shortSummary());

    // Blocks for richer layout
    List<Map<String, Object>> blocks = new ArrayList<>();

    // Header
    Map<String, Object> header = new HashMap<>();
    header.put("type", "header");
    header.put(
        "text",
        Map.of("type", "plain_text", "text", emoji + " " + context.shortSummary(), "emoji", true));
    blocks.add(header);

    // Details section
    List<Map<String, Object>> fields = new ArrayList<>();
    fields.add(
        Map.of(
            "type",
            "mrkdwn",
            "text",
            "*Workflow:*\n"
                + (context.pipelineName() != null ? context.pipelineName() : "Unknown")));
    fields.add(
        Map.of(
            "type",
            "mrkdwn",
            "text",
            "*Status:*\n" + (context.status() != null ? context.status() : context.eventType())));

    if (context.stageName() != null) {
      fields.add(Map.of("type", "mrkdwn", "text", "*Stage:*\n" + context.stageName()));
    }

    if (context.environment() != null) {
      fields.add(Map.of("type", "mrkdwn", "text", "*Environment:*\n" + context.environment()));
    }

    Map<String, Object> fieldsSection = new HashMap<>();
    fieldsSection.put("type", "section");
    fieldsSection.put("fields", fields);
    blocks.add(fieldsSection);

    // Error message if present
    if (context.errorMessage() != null && !context.errorMessage().isBlank()) {
      Map<String, Object> errorSection = new HashMap<>();
      errorSection.put("type", "section");
      errorSection.put(
          "text",
          Map.of(
              "type",
              "mrkdwn",
              "text",
              "*Error:*\n```" + truncate(context.errorMessage(), 500) + "```"));
      blocks.add(errorSection);
    }

    // Action buttons
    if (context.executionUrl() != null) {
      Map<String, Object> actions = new HashMap<>();
      actions.put("type", "actions");
      List<Map<String, Object>> elements = new ArrayList<>();
      elements.add(
          Map.of(
              "type",
              "button",
              "text",
              Map.of("type", "plain_text", "text", "View Run"),
              "url",
              context.executionUrl(),
              "style",
              "primary"));
      if (context.pipelineUrl() != null) {
        elements.add(
            Map.of(
                "type", "button",
                "text", Map.of("type", "plain_text", "text", "View Workflow"),
                "url", context.pipelineUrl()));
      }
      actions.put("elements", elements);
      blocks.add(actions);
    }

    attachment.put("blocks", blocks);
    attachments.add(attachment);
    payload.put("attachments", attachments);

    return objectMapper.writeValueAsString(payload);
  }

  private String getColorForEvent(String eventType) {
    return switch (eventType) {
      case "execution.failed", "job.failed" -> "#dc3545"; // red
      case "execution.timeout" -> "#fd7e14"; // orange
      case "execution.cancelled" -> "#6c757d"; // gray
      case "execution.completed" -> "#28a745"; // green
      default -> "#007bff"; // blue
    };
  }

  private String getEmojiForEvent(String eventType) {
    return switch (eventType) {
      case "execution.failed", "job.failed" -> "🚨";
      case "execution.timeout" -> "⏰";
      case "execution.cancelled" -> "🛑";
      case "execution.completed" -> "✅";
      default -> "📢";
    };
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }
}

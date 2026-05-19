package io.pravah.notification.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.notification.application.dto.AlertContext;
import io.pravah.notification.domain.NotificationType;
import io.pravah.notification.infrastructure.channel.DeliveryResult;
import io.pravah.notification.infrastructure.channel.NotificationChannel;
import io.pravah.notification.infrastructure.persistence.entity.AlertHistoryEntity;
import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import io.pravah.notification.infrastructure.persistence.repository.AlertHistoryRepository;
import io.pravah.notification.infrastructure.persistence.repository.AlertRuleRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for dispatching alerts through configured channels. Handles rule matching,
 * deduplication, and delivery tracking.
 */
@Service
public class AlertDispatchService {

  private static final Logger log = LoggerFactory.getLogger(AlertDispatchService.class);
  private static final TypeReference<List<Map<String, Object>>> CHANNEL_LIST_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> CONDITIONS_TYPE =
      new TypeReference<>() {};

  private final AlertRuleRepository alertRuleRepository;
  private final AlertHistoryRepository alertHistoryRepository;
  private final UserNotificationService userNotificationService;
  private final Map<String, NotificationChannel> channelsByType;
  private final ObjectMapper objectMapper;
  private final String uiBaseUrl;

  public AlertDispatchService(
      AlertRuleRepository alertRuleRepository,
      AlertHistoryRepository alertHistoryRepository,
      UserNotificationService userNotificationService,
      List<NotificationChannel> channels,
      ObjectMapper objectMapper,
      @Value("${pravah.ui.base-url:http://localhost:5173}") String uiBaseUrl) {
    this.alertRuleRepository = alertRuleRepository;
    this.alertHistoryRepository = alertHistoryRepository;
    this.userNotificationService = userNotificationService;
    this.objectMapper = objectMapper;
    this.uiBaseUrl = uiBaseUrl;

    // Index channels by type
    this.channelsByType = new java.util.HashMap<>();
    for (NotificationChannel channel : channels) {
      channelsByType.put(channel.type(), channel);
    }
    log.info("Initialized alert dispatch with channels: {}", channelsByType.keySet());
  }

  /**
   * Process an execution event and dispatch alerts for matching rules.
   *
   * @param tenantId Tenant ID
   * @param pipelineId Pipeline ID
   * @param context Alert context with event details
   */
  @Transactional
  public void processAlert(UUID tenantId, UUID pipelineId, AlertContext context) {
    TenantContext.setCurrentTenantId(tenantId);
    try {
      // Find matching rules (pipeline-specific + tenant-wide)
      List<AlertRuleEntity> rules =
          alertRuleRepository.findEnabledRulesForPipeline(tenantId, pipelineId);

      if (rules.isEmpty()) {
        log.debug(
            "No alert rules matched",
            kv("tenant_id", tenantId),
            kv("pipeline_id", pipelineId),
            kv("event_type", context.eventType()));
        return;
      }

      for (AlertRuleEntity rule : rules) {
        processRuleAlert(rule, context);
      }

    } finally {
      TenantContext.clear();
    }
  }

  private void processRuleAlert(AlertRuleEntity rule, AlertContext context) {
    try {
      // Check conditions match
      if (!matchesConditions(rule, context)) {
        log.debug(
            "Rule conditions not matched",
            kv("rule_id", rule.getId()),
            kv("event_type", context.eventType()));
        return;
      }

      // Check deduplication
      String dedupKey = computeDedupKey(rule.getId(), context);
      Instant windowStart = Instant.now().minusSeconds(rule.getDedupWindowSeconds());

      if (alertHistoryRepository.existsRecentDuplicate(
          rule.getTenantId(), rule.getId(), dedupKey, windowStart)) {
        log.debug("Alert deduplicated", kv("rule_id", rule.getId()), kv("dedup_key", dedupKey));
        return;
      }

      // Parse channels config
      List<Map<String, Object>> channels =
          objectMapper.readValue(rule.getChannels(), CHANNEL_LIST_TYPE);
      if (channels.isEmpty()) {
        log.warn("Rule has no channels configured", kv("rule_id", rule.getId()));
        return;
      }

      // Create history entry
      AlertContext contextWithUiUrl =
          new AlertContext(
              context.tenantId(),
              context.executionId(),
              context.pipelineId(),
              context.pipelineName(),
              context.eventType(),
              context.status(),
              context.errorMessage(),
              context.stageName(),
              context.occurredAt(),
              context.environment(),
              context.metadata(),
              uiBaseUrl);

      AlertHistoryEntity history =
          new AlertHistoryEntity(
              rule.getTenantId(),
              rule.getId(),
              context.eventType(),
              objectMapper.writeValueAsString(context),
              dedupKey);

      // Dispatch to all configured channels
      List<Map<String, Object>> deliveryStatuses = new ArrayList<>();

      for (Map<String, Object> channelConfig : channels) {
        String channelType = (String) channelConfig.get("type");
        NotificationChannel channel = channelsByType.get(channelType);

        if (channel == null) {
          log.warn("Unknown channel type", kv("type", channelType), kv("rule_id", rule.getId()));
          deliveryStatuses.add(
              Map.of(
                  "channel", channelType,
                  "status", "skipped",
                  "error", "Unknown channel type"));
          continue;
        }

        DeliveryResult result = channel.send(contextWithUiUrl, channelConfig);
        deliveryStatuses.add(
            Map.of(
                "channel", result.channel(),
                "status", result.success() ? "sent" : "failed",
                "message", result.message(),
                "sentAt", result.sentAt().toString(),
                "error", result.error() != null ? result.error() : ""));
      }

      // Save history with delivery status
      history.setDeliveryStatus(objectMapper.writeValueAsString(deliveryStatuses));
      alertHistoryRepository.save(history);

      log.info(
          "Alert dispatched",
          kv("rule_id", rule.getId()),
          kv("rule_name", rule.getName()),
          kv("event_type", context.eventType()),
          kv("channels_count", channels.size()));

      createInAppNotification(rule, contextWithUiUrl, history.getId());

    } catch (Exception e) {
      log.error(
          "Failed to process alert rule",
          kv("rule_id", rule.getId()),
          kv("event_type", context.eventType()),
          e);
    }
  }

  @SuppressWarnings("unchecked")
  private boolean matchesConditions(AlertRuleEntity rule, AlertContext context) {
    try {
      Map<String, Object> conditions =
          objectMapper.readValue(rule.getConditions(), CONDITIONS_TYPE);
      if (conditions.isEmpty()) {
        return true; // No conditions = match all
      }

      // Check event types filter
      List<String> eventTypes = (List<String>) conditions.get("events");
      if (eventTypes != null && !eventTypes.isEmpty()) {
        if (!eventTypes.contains(context.eventType())) {
          return false;
        }
      }

      // Check environments filter
      List<String> environments = (List<String>) conditions.get("environments");
      if (environments != null && !environments.isEmpty() && context.environment() != null) {
        if (!environments.contains(context.environment())) {
          return false;
        }
      }

      return true;

    } catch (Exception e) {
      log.warn("Failed to parse rule conditions", kv("rule_id", rule.getId()), e);
      return false;
    }
  }

  private void createInAppNotification(
      AlertRuleEntity rule, AlertContext context, UUID alertHistoryId) {
    NotificationType type =
        context.eventType().contains("failed") || context.eventType().contains("timeout")
            ? NotificationType.ALERT
            : NotificationType.INFO;

    userNotificationService.createNotification(
        rule.getTenantId(),
        rule.getCreatedBy(),
        context.shortSummary(),
        context.errorMessage(),
        type,
        "execution",
        context.executionId(),
        context.executionUrl(),
        alertHistoryId);
  }

  private String computeDedupKey(UUID ruleId, AlertContext context) {
    // Dedup key: hash of rule + pipeline + event type + execution
    String input =
        ruleId.toString()
            + "|"
            + (context.pipelineId() != null ? context.pipelineId().toString() : "")
            + "|"
            + context.eventType()
            + "|"
            + (context.executionId() != null ? context.executionId().toString() : "");

    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, 32);
    } catch (Exception e) {
      return input.hashCode() + "";
    }
  }
}

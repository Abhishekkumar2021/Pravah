package io.pravah.scheduler.api.dto;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PipelineTriggerResponse(
    UUID id,
    UUID pipelineId,
    String name,
    String triggerType,
    Map<String, Object> config,
    boolean enabled,
    Instant lastTriggeredAt,
    Instant createdAt,
    String webhookUrl,
    String webhookSecret) {

  public static PipelineTriggerResponse from(
      PipelineTrigger trigger,
      Map<String, Object> config,
      String webhookUrl,
      String webhookSecret) {
    return new PipelineTriggerResponse(
        trigger.getId(),
        trigger.getPipelineId(),
        trigger.getName(),
        trigger.getTriggerType().value(),
        config,
        trigger.isEnabled(),
        trigger.getLastTriggeredAt(),
        trigger.getCreatedAt(),
        webhookUrl,
        webhookSecret);
  }

  public static PipelineTriggerResponse from(PipelineTrigger trigger, Map<String, Object> config) {
    return from(trigger, config, null, null);
  }
}

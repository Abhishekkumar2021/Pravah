package io.pravah.scheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineTriggerTest {

  @Test
  void builder_createsValidTrigger() {
    UUID tenantId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();

    PipelineTrigger trigger =
        PipelineTrigger.builder()
            .tenantId(tenantId)
            .pipelineId(pipelineId)
            .name("test-trigger")
            .triggerType(TriggerType.WEBHOOK)
            .config("{\"rateLimitPerMinute\":60}")
            .secretHash("hashed")
            .createdBy(createdBy)
            .build();

    assertThat(trigger.getId()).isNotNull();
    assertThat(trigger.getTenantId()).isEqualTo(tenantId);
    assertThat(trigger.getPipelineId()).isEqualTo(pipelineId);
    assertThat(trigger.getName()).isEqualTo("test-trigger");
    assertThat(trigger.getTriggerType()).isEqualTo(TriggerType.WEBHOOK);
    assertThat(trigger.getConfig()).isEqualTo("{\"rateLimitPerMinute\":60}");
    assertThat(trigger.getSecretHash()).isEqualTo("hashed");
    assertThat(trigger.isEnabled()).isTrue();
    assertThat(trigger.getCreatedBy()).isEqualTo(createdBy);
  }

  @Test
  void builder_requiresTenantId() {
    assertThatThrownBy(
            () ->
                PipelineTrigger.builder()
                    .pipelineId(UUID.randomUUID())
                    .name("test")
                    .triggerType(TriggerType.WEBHOOK)
                    .createdBy(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantId is required");
  }

  @Test
  void builder_requiresPipelineId() {
    assertThatThrownBy(
            () ->
                PipelineTrigger.builder()
                    .tenantId(UUID.randomUUID())
                    .name("test")
                    .triggerType(TriggerType.WEBHOOK)
                    .createdBy(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pipelineId is required");
  }

  @Test
  void builder_requiresName() {
    assertThatThrownBy(
            () ->
                PipelineTrigger.builder()
                    .tenantId(UUID.randomUUID())
                    .pipelineId(UUID.randomUUID())
                    .triggerType(TriggerType.WEBHOOK)
                    .createdBy(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("name is required");
  }

  @Test
  void builder_requiresTriggerType() {
    assertThatThrownBy(
            () ->
                PipelineTrigger.builder()
                    .tenantId(UUID.randomUUID())
                    .pipelineId(UUID.randomUUID())
                    .name("test")
                    .createdBy(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("triggerType is required");
  }

  @Test
  void builder_requiresCreatedBy() {
    assertThatThrownBy(
            () ->
                PipelineTrigger.builder()
                    .tenantId(UUID.randomUUID())
                    .pipelineId(UUID.randomUUID())
                    .name("test")
                    .triggerType(TriggerType.WEBHOOK)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("createdBy is required");
  }

  @Test
  void disable_setsEnabledFalse() {
    PipelineTrigger trigger = createTrigger();
    assertThat(trigger.isEnabled()).isTrue();

    trigger.disable();
    assertThat(trigger.isEnabled()).isFalse();
  }

  @Test
  void enable_setsEnabledTrue() {
    PipelineTrigger trigger = createTrigger();
    trigger.disable();

    trigger.enable();
    assertThat(trigger.isEnabled()).isTrue();
  }

  @Test
  void recordTriggered_updatesLastTriggeredAt() {
    PipelineTrigger trigger = createTrigger();
    assertThat(trigger.getLastTriggeredAt()).isNull();

    trigger.recordTriggered();
    assertThat(trigger.getLastTriggeredAt()).isNotNull();
  }

  @Test
  void updateName_updatesName() {
    PipelineTrigger trigger = createTrigger();
    trigger.updateName("new-name");
    assertThat(trigger.getName()).isEqualTo("new-name");
  }

  @Test
  void updateName_trimsWhitespace() {
    PipelineTrigger trigger = createTrigger();
    trigger.updateName("  trimmed  ");
    assertThat(trigger.getName()).isEqualTo("trimmed");
  }

  @Test
  void updateName_rejectsBlankName() {
    PipelineTrigger trigger = createTrigger();
    assertThatThrownBy(() -> trigger.updateName("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name is required");
  }

  @Test
  void updateConfig_updatesConfig() {
    PipelineTrigger trigger = createTrigger();
    trigger.updateConfig("{\"new\":\"config\"}");
    assertThat(trigger.getConfig()).isEqualTo("{\"new\":\"config\"}");
  }

  @Test
  void updateConfig_handlesNullAsEmptyJson() {
    PipelineTrigger trigger = createTrigger();
    trigger.updateConfig(null);
    assertThat(trigger.getConfig()).isEqualTo("{}");
  }

  private PipelineTrigger createTrigger() {
    return PipelineTrigger.builder()
        .tenantId(UUID.randomUUID())
        .pipelineId(UUID.randomUUID())
        .name("test-trigger")
        .triggerType(TriggerType.WEBHOOK)
        .createdBy(UUID.randomUUID())
        .build();
  }
}

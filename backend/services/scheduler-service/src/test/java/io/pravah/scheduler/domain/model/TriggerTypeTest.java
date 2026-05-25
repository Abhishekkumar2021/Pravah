package io.pravah.scheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TriggerTypeTest {

  @Test
  void fromValue_parsesWebhook() {
    assertThat(TriggerType.fromValue("webhook")).isEqualTo(TriggerType.WEBHOOK);
    assertThat(TriggerType.fromValue("WEBHOOK")).isEqualTo(TriggerType.WEBHOOK);
    assertThat(TriggerType.fromValue("Webhook")).isEqualTo(TriggerType.WEBHOOK);
  }

  @Test
  void fromValue_parsesKafka() {
    assertThat(TriggerType.fromValue("kafka")).isEqualTo(TriggerType.KAFKA);
    assertThat(TriggerType.fromValue("KAFKA")).isEqualTo(TriggerType.KAFKA);
  }

  @Test
  void fromValue_throwsForUnknown() {
    assertThatThrownBy(() -> TriggerType.fromValue("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown trigger type: unknown");
  }

  @Test
  void fromValue_throwsForNull() {
    assertThatThrownBy(() -> TriggerType.fromValue(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trigger type is required");
  }

  @Test
  void value_returnsLowercase() {
    assertThat(TriggerType.WEBHOOK.value()).isEqualTo("webhook");
    assertThat(TriggerType.KAFKA.value()).isEqualTo("kafka");
  }
}

package io.pravah.execution.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionRealtimeChannelsTest {

  @Test
  void topic_tenantFromTopic_roundTrip() {
    UUID tenantId = UUID.randomUUID();
    assertThat(ExecutionRealtimeChannels.tenantFromTopic(ExecutionRealtimeChannels.topic(tenantId)))
        .isEqualTo(tenantId);
  }

  @Test
  void tenantFromTopic_wrongPrefix_throws() {
    assertThatThrownBy(
            () ->
                ExecutionRealtimeChannels.tenantFromTopic(
                    "wrong:" + UUID.randomUUID() + ":executions"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerConfigSupportTest {

  @Test
  void parseConfig_returnsEmptyMapForNull() {
    assertThat(TriggerConfigSupport.parseConfig(null)).isEmpty();
    assertThat(TriggerConfigSupport.parseConfig("")).isEmpty();
    assertThat(TriggerConfigSupport.parseConfig("  ")).isEmpty();
  }

  @Test
  void parseConfig_parsesValidJson() {
    Map<String, Object> config = TriggerConfigSupport.parseConfig("{\"topic\":\"orders\"}");
    assertThat(config).containsEntry("topic", "orders");
  }

  @Test
  void parseConfig_throwsOnInvalidJson() {
    assertThatThrownBy(() -> TriggerConfigSupport.parseConfig("{invalid}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid trigger config JSON");
  }

  @Test
  void toJson_convertsMapToJson() {
    String json = TriggerConfigSupport.toJson(Map.of("topic", "orders"));
    assertThat(json).contains("topic").contains("orders");
  }

  @Test
  void toJson_handlesNullMap() {
    String json = TriggerConfigSupport.toJson(null);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void rateLimitPerMinute_returnsDefaultWhenMissing() {
    assertThat(TriggerConfigSupport.rateLimitPerMinute(Map.of())).isEqualTo(60);
  }

  @Test
  void rateLimitPerMinute_returnsConfiguredValue() {
    assertThat(TriggerConfigSupport.rateLimitPerMinute(Map.of("rateLimitPerMinute", 100)))
        .isEqualTo(100);
  }

  @Test
  void rateLimitPerMinute_enforcesMinimum() {
    assertThat(TriggerConfigSupport.rateLimitPerMinute(Map.of("rateLimitPerMinute", 0)))
        .isEqualTo(1);
    assertThat(TriggerConfigSupport.rateLimitPerMinute(Map.of("rateLimitPerMinute", -5)))
        .isEqualTo(1);
  }

  @Test
  void requireKafkaTopic_returnsTopicWhenPresent() {
    String topic = TriggerConfigSupport.requireKafkaTopic(Map.of("topic", "orders"));
    assertThat(topic).isEqualTo("orders");
  }

  @Test
  void requireKafkaTopic_throwsWhenMissing() {
    assertThatThrownBy(() -> TriggerConfigSupport.requireKafkaTopic(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Kafka trigger requires config.topic");
  }

  @Test
  void requireKafkaTopic_throwsWhenBlank() {
    assertThatThrownBy(() -> TriggerConfigSupport.requireKafkaTopic(Map.of("topic", "  ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void kafkaFilter_returnsFilterMap() {
    Map<String, Object> config = Map.of("filter", Map.of("eventType", "created"));
    Map<String, Object> filter = TriggerConfigSupport.kafkaFilter(config);
    assertThat(filter).containsEntry("eventType", "created");
  }

  @Test
  void kafkaFilter_returnsEmptyMapWhenMissing() {
    assertThat(TriggerConfigSupport.kafkaFilter(Map.of())).isEmpty();
  }

  @Test
  void matchesKafkaFilter_requiresAllFields() {
    Map<String, Object> filter = Map.of("eventType", "created");
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, Map.of("eventType", "created")))
        .isTrue();
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, Map.of("eventType", "deleted")))
        .isFalse();
  }

  @Test
  void matchesKafkaFilter_emptyFilterMatchesAll() {
    assertThat(TriggerConfigSupport.matchesKafkaFilter(Map.of(), Map.of("any", "value"))).isTrue();
  }

  @Test
  void matchesKafkaFilter_returnsFalseForNullPayload() {
    Map<String, Object> filter = Map.of("eventType", "created");
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, null)).isFalse();
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, Map.of())).isFalse();
  }

  @Test
  void matchesKafkaFilter_handlesMultipleFields() {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("eventType", "created");
    filter.put("source", "api");

    Map<String, Object> matchingPayload = new LinkedHashMap<>();
    matchingPayload.put("eventType", "created");
    matchingPayload.put("source", "api");
    matchingPayload.put("extra", "field");
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, matchingPayload)).isTrue();

    Map<String, Object> partialPayload = Map.of("eventType", "created");
    assertThat(TriggerConfigSupport.matchesKafkaFilter(filter, partialPayload)).isFalse();
  }
}

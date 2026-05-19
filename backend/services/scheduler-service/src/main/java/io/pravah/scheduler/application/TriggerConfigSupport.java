package io.pravah.scheduler.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TriggerConfigSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TriggerConfigSupport() {}

  public static Map<String, Object> parseConfig(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid trigger config JSON: " + e.getMessage());
    }
  }

  public static String toJson(Map<String, Object> config) {
    try {
      return MAPPER.writeValueAsString(config != null ? config : Map.of());
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid trigger config: " + e.getMessage());
    }
  }

  public static int rateLimitPerMinute(Map<String, Object> config) {
    Object raw = config.get("rateLimitPerMinute");
    if (raw instanceof Number number) {
      return Math.max(1, number.intValue());
    }
    return 60;
  }

  public static String requireKafkaTopic(Map<String, Object> config) {
    Object topic = config.get("topic");
    if (topic == null || topic.toString().isBlank()) {
      throw new IllegalArgumentException("Kafka trigger requires config.topic");
    }
    return topic.toString().trim();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> kafkaFilter(Map<String, Object> config) {
    Object filter = config.get("filter");
    if (filter instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((k, v) -> result.put(String.valueOf(k), v));
      return Collections.unmodifiableMap(result);
    }
    return Map.of();
  }

  public static boolean matchesKafkaFilter(
      Map<String, Object> filter, Map<String, Object> payload) {
    if (filter.isEmpty()) {
      return true;
    }
    if (payload == null || payload.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      Object actual = payload.get(entry.getKey());
      if (actual == null || !actual.toString().equals(String.valueOf(entry.getValue()))) {
        return false;
      }
    }
    return true;
  }
}

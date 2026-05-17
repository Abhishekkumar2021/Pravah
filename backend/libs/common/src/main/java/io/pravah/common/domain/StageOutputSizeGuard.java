package io.pravah.common.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enforces stage output size limits at job completion (US-02.10).
 *
 * <p>Callers should log {@link StageOutputSizeEnforcement#exceededWarnThreshold()}, {@link
 * StageOutputSizeEnforcement#truncated()}, and {@link
 * StageOutputSizeEnforcement#serializationFailed()} using structured logging (e.g. logstash {@code
 * kv}).
 */
public final class StageOutputSizeGuard {

  private StageOutputSizeGuard() {}

  /**
   * Applies size limits to stage output before persistence.
   *
   * @param output the raw stage output map (may be null)
   * @param maxBytes maximum serialized JSON size
   * @param warnBytes size above which {@code exceededWarnThreshold} is set
   * @param objectMapper JSON serializer for size measurement
   * @return enforcement result for the caller to log and persist
   */
  public static StageOutputSizeEnforcement enforce(
      Map<String, Object> output, long maxBytes, long warnBytes, ObjectMapper objectMapper) {
    if (output == null || output.isEmpty()) {
      return new StageOutputSizeEnforcement(
          output == null ? Map.of() : output, 0, false, false, false);
    }

    int bytes = serializedSize(output, objectMapper);
    if (bytes == StageOutputSizeEnforcement.SERIALIZATION_FAILED) {
      return new StageOutputSizeEnforcement(
          truncatedSummary(output, maxBytes, 0, objectMapper),
          StageOutputSizeEnforcement.SERIALIZATION_FAILED,
          true,
          false,
          true);
    }

    if (bytes <= maxBytes) {
      boolean warn = bytes > warnBytes;
      return new StageOutputSizeEnforcement(output, bytes, false, warn, false);
    }

    return new StageOutputSizeEnforcement(
        truncatedSummary(output, maxBytes, bytes, objectMapper), bytes, true, false, false);
  }

  private static Map<String, Object> truncatedSummary(
      Map<String, Object> output, long maxBytes, int originalBytes, ObjectMapper objectMapper) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("_truncated", true);
    if (originalBytes > 0) {
      summary.put("_original_bytes", originalBytes);
    }
    summary.put("_max_bytes", maxBytes);
    copySmallMetadata(output, summary, objectMapper, maxBytes / 4);
    return summary;
  }

  private static void copySmallMetadata(
      Map<String, Object> output,
      Map<String, Object> summary,
      ObjectMapper objectMapper,
      long perKeyBudget) {
    for (Map.Entry<String, Object> entry : output.entrySet()) {
      if (entry.getKey().startsWith("_")) {
        continue;
      }
      Map<String, Object> single = Map.of(entry.getKey(), entry.getValue());
      if (serializedSize(single, objectMapper) <= perKeyBudget) {
        summary.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private static int serializedSize(Map<String, Object> value, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsBytes(value).length;
    } catch (JsonProcessingException e) {
      return StageOutputSizeEnforcement.SERIALIZATION_FAILED;
    }
  }
}

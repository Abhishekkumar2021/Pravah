package io.pravah.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Serializes runner stage output for gRPC {@code JobStatusUpdate.output_json}. */
final class RunnerOutputJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private RunnerOutputJson() {}

  static String toJson(Map<String, Object> output) {
    if (output == null || output.isEmpty()) {
      return "";
    }
    try {
      return MAPPER.writeValueAsString(output);
    } catch (JsonProcessingException e) {
      return "{\"error\":\"failed to serialize output\"}";
    }
  }
}

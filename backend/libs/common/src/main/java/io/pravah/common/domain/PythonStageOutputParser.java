package io.pravah.common.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses structured stage output from Python stdout (US-02.15). */
public final class PythonStageOutputParser {

  private static final Logger log = LoggerFactory.getLogger(PythonStageOutputParser.class);

  public static final String OUTPUT_LINE_PREFIX = "__PRAVAH_OUTPUT__:";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PythonStageOutputParser() {}

  /**
   * Extracts structured output from process stdout.
   *
   * <p>Convention (first match wins, scanning from last non-blank line upward):
   *
   * <ul>
   *   <li>Line prefixed with {@link #OUTPUT_LINE_PREFIX} followed by JSON object
   *   <li>Line that is a single JSON object ({@code { ... }})
   * </ul>
   */
  public static Optional<Map<String, Object>> parseStdout(String stdout) {
    if (stdout == null || stdout.isBlank()) {
      return Optional.empty();
    }
    List<String> lines = stdout.lines().filter(line -> !line.isBlank()).toList();
    for (int i = lines.size() - 1; i >= 0; i--) {
      String line = lines.get(i).trim();
      Optional<Map<String, Object>> parsed = parseLine(line);
      if (parsed.isPresent()) {
        return parsed;
      }
    }
    return Optional.empty();
  }

  private static Optional<Map<String, Object>> parseLine(String line) {
    if (line.startsWith(OUTPUT_LINE_PREFIX)) {
      return parseJsonObject(line.substring(OUTPUT_LINE_PREFIX.length()).trim());
    }
    if (line.startsWith("{") && line.endsWith("}")) {
      return parseJsonObject(line);
    }
    return Optional.empty();
  }

  private static Optional<Map<String, Object>> parseJsonObject(String json) {
    try {
      Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {});
      if (map == null || map.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new LinkedHashMap<>(map));
    } catch (Exception e) {
      log.trace("Failed to parse JSON from stdout line: {}", json, e);
      return Optional.empty();
    }
  }
}

package io.pravah.tenant.application.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses role permission JSON stored in the database. */
public final class RolePermissions {

  private static final Logger log = LoggerFactory.getLogger(RolePermissions.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private RolePermissions() {}

  /**
   * Parses a JSON array of permission strings.
   *
   * @param permissionsJson JSON array string, e.g. {@code ["users:read", "pipelines:*"]}
   * @return list of permission strings
   * @throws IllegalStateException if the JSON is malformed (indicates data corruption)
   */
  public static List<String> parse(String permissionsJson) {
    if (permissionsJson == null || permissionsJson.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(permissionsJson, STRING_LIST);
    } catch (JsonProcessingException e) {
      log.error("Failed to parse role permissions JSON", kv("json", permissionsJson), e);
      throw new IllegalStateException("Invalid permissions JSON in database: " + e.getMessage(), e);
    }
  }

  /** Serializes permission strings to JSON for JSONB storage. */
  public static String toJson(List<String> permissions) {
    Objects.requireNonNull(permissions, "permissions");
    if (permissions.isEmpty()) {
      throw new IllegalArgumentException("At least one permission is required");
    }
    try {
      return MAPPER.writeValueAsString(permissions);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize permissions", e);
    }
  }
}

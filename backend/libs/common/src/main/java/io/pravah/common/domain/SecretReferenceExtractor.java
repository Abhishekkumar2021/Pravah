package io.pravah.common.domain;

import io.pravah.common.domain.resolution.ValueReferenceParser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts {@code ${secret.name}} references from pipeline definitions for validation.
 *
 * <p>Used during pipeline publish to verify all referenced secrets exist in tenant_secrets.
 */
public final class SecretReferenceExtractor {

  private SecretReferenceExtractor() {}

  /**
   * Extracts all unique secret names referenced in a pipeline definition.
   *
   * @param definition the pipeline definition map
   * @return set of secret names (e.g., "api_key", "db_password")
   */
  public static Set<String> extract(Map<String, Object> definition) {
    Set<String> secrets = new HashSet<>();
    if (definition == null) {
      return secrets;
    }
    extractFromValue(definition, secrets);
    return secrets;
  }

  private static void extractFromValue(Object value, Set<String> secrets) {
    if (value instanceof String s) {
      secrets.addAll(ValueReferenceParser.extractSecretNames(s));
    } else if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = entry.getKey().toString();
        if ("variables".equals(key) || "environments".equals(key)) {
          continue;
        }
        extractFromValue(entry.getValue(), secrets);
      }
    } else if (value instanceof List<?> list) {
      for (Object item : list) {
        extractFromValue(item, secrets);
      }
    }
  }
}

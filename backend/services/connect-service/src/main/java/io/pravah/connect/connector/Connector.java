package io.pravah.connect.connector;

import io.pravah.connect.domain.ConnectorSpec;
import java.util.Map;

/**
 * Base interface for all data connectors.
 *
 * <p>Connectors are responsible for:
 *
 * <ul>
 *   <li>Providing their specification (config schema, capabilities)
 *   <li>Validating configuration
 *   <li>Testing connectivity
 *   <li>Reading data (source connectors)
 *   <li>Writing data (sink connectors)
 * </ul>
 */
public interface Connector {

  /** Returns the connector specification. */
  ConnectorSpec getSpec();

  /**
   * Validates the provided configuration.
   *
   * @param config the configuration to validate
   * @return validation result
   */
  ValidationResult validate(Map<String, Object> config);

  /**
   * Tests connectivity with the provided configuration.
   *
   * @param config the configuration to test
   * @return test result
   */
  TestResult testConnection(Map<String, Object> config);

  /** Validation result for connector configuration. */
  record ValidationResult(boolean valid, Map<String, String> errors) {
    public static ValidationResult success() {
      return new ValidationResult(true, Map.of());
    }

    public static ValidationResult failure(Map<String, String> errors) {
      return new ValidationResult(false, errors);
    }

    public static ValidationResult failure(String field, String message) {
      return new ValidationResult(false, Map.of(field, message));
    }
  }

  /** Result of a connection test. */
  record TestResult(boolean success, String message, long latencyMs, Map<String, Object> metadata) {
    public static TestResult success(String message, long latencyMs) {
      return new TestResult(true, message, latencyMs, Map.of());
    }

    public static TestResult success(String message, long latencyMs, Map<String, Object> metadata) {
      return new TestResult(true, message, latencyMs, metadata);
    }

    public static TestResult failure(String message) {
      return new TestResult(false, message, 0, Map.of());
    }

    public static TestResult failure(String message, Throwable cause) {
      return new TestResult(false, message + ": " + cause.getMessage(), 0, Map.of());
    }
  }
}

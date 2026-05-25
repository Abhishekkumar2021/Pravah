package io.pravah.pipeline.domain;

import io.pravah.common.net.UrlSafetyValidator;
import java.util.Map;

/**
 * Validates and builds JDBC settings from postgres connection config.
 *
 * <p>Expected config structure:
 *
 * <pre>{@code
 * {
 *   "host": "localhost",       // required (unless url provided)
 *   "port": 5432,              // optional, defaults to 5432
 *   "database": "mydb",        // required (unless url provided)
 *   "username": "user",        // required
 *   "url": "jdbc:...",         // optional, overrides host/port/database
 *   "credentials": {
 *     "password": "env:VAR"    // required for production
 *   }
 * }
 * }</pre>
 */
public final class PostgresConnectionConfig {

  private PostgresConnectionConfig() {}

  /**
   * Validates the postgres connection config has all required fields.
   *
   * @throws IllegalArgumentException if validation fails
   */
  @SuppressWarnings("unchecked")
  public static void validate(Map<String, Object> config) {
    validate(config, false);
  }

  @SuppressWarnings("unchecked")
  public static void validate(Map<String, Object> config, boolean allowPrivateNetworkTargets) {
    resolveJdbcUrl(config, allowPrivateNetworkTargets);
    resolveUsername(config);

    Object credentials = config.get("credentials");
    if (credentials == null) {
      throw new IllegalArgumentException(
          "Postgres connection requires 'credentials.password' (use env:VAR_NAME reference)");
    }
    if (!(credentials instanceof Map)) {
      throw new IllegalArgumentException("config.credentials must be an object");
    }
    Map<String, Object> creds = (Map<String, Object>) credentials;
    Object password = creds.get("password");
    if (password == null || password.toString().isBlank()) {
      throw new IllegalArgumentException(
          "Postgres connection requires 'credentials.password' (use env:VAR_NAME reference)");
    }
  }

  public static String resolveJdbcUrl(Map<String, Object> config) {
    return resolveJdbcUrl(config, false);
  }

  public static String resolveJdbcUrl(
      Map<String, Object> config, boolean allowPrivateNetworkTargets) {
    String explicitUrl = getString(config, "url");
    if (explicitUrl != null && !explicitUrl.isBlank()) {
      if (!explicitUrl.startsWith("jdbc:")) {
        throw new IllegalArgumentException("Postgres connection url must start with 'jdbc:'");
      }
      if (!allowPrivateNetworkTargets) {
        UrlSafetyValidator.validateJdbcTarget(explicitUrl);
      }
      return explicitUrl;
    }

    String host = require(config, "host");
    if (!allowPrivateNetworkTargets) {
      UrlSafetyValidator.validateJdbcTarget(host);
    }
    String database = require(config, "database");
    int port = parsePort(config.get("port"));
    return "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
  }

  public static String resolveUsername(Map<String, Object> config) {
    return require(config, "username");
  }

  private static String require(Map<String, Object> config, String key) {
    String value = getString(config, key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Postgres connection config missing required '%s'".formatted(key));
    }
    return value;
  }

  private static int parsePort(Object portObj) {
    if (portObj == null) {
      return 5432;
    }
    if (portObj instanceof Number n) {
      return n.intValue();
    }
    return Integer.parseInt(portObj.toString());
  }

  private static String getString(Map<String, Object> config, String key) {
    Object v = config.get(key);
    return v != null ? v.toString() : null;
  }
}

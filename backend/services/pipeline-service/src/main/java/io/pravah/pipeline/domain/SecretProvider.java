package io.pravah.pipeline.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Supported secret providers for tenant secrets.
 *
 * <p>Each provider has its own path format:
 *
 * <ul>
 *   <li>{@code ENV} — environment variable name (e.g., "MY_API_KEY")
 *   <li>{@code VAULT} — Vault path with key (e.g., "secret/data/myapp#api_key")
 *   <li>{@code AWS_SM} — AWS Secrets Manager ARN (future)
 * </ul>
 */
public enum SecretProvider {
  ENV,
  VAULT,
  AWS_SM;

  public static final Set<String> SUPPORTED = Set.of("env", "vault", "aws_sm");

  public static SecretProvider parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Secret provider is required");
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "env" -> ENV;
      case "vault" -> VAULT;
      case "aws_sm", "awssm", "aws-sm" -> AWS_SM;
      default ->
          throw new IllegalArgumentException(
              "Unknown secret provider: '%s'. Supported: %s".formatted(raw, SUPPORTED));
    };
  }

  public String toValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}

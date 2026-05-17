package io.pravah.pipeline.application;

/**
 * Resolved secret value for internal execution-time use only.
 *
 * <p>Never expose this DTO on public APIs or log the {@code value} field.
 */
public record ResolvedSecretValue(String name, String value) {}

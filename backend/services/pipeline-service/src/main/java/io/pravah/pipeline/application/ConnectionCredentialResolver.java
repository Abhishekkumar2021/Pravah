package io.pravah.pipeline.application;

import io.pravah.common.domain.resolution.EnvRef;
import io.pravah.common.domain.resolution.EnvResolverProvider;
import io.pravah.common.domain.resolution.LiteralValue;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.SecretRef;
import io.pravah.common.domain.resolution.ValueReference;
import io.pravah.common.domain.resolution.ValueReferenceParser;
import io.pravah.common.domain.resolution.VaultRef;
import io.pravah.common.domain.resolution.VaultResolverProvider;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates credentials from connection config using the unified resolution system.
 *
 * <p>Credentials are stored in {@code config.credentials.password} as a reference string:
 *
 * <ul>
 *   <li>{@code env:VAR_NAME} — reads from environment variable (local dev)
 *   <li>{@code vault:path#key} — reads from HashiCorp Vault (production)
 *   <li>{@code ${secret.name}} — reads from tenant secrets table
 * </ul>
 *
 * <p>Example config:
 *
 * <pre>{@code
 * {
 *   "host": "localhost",
 *   "credentials": {
 *     "password": "env:PRAVAH_DB_PASSWORD"
 *   }
 * }
 * }</pre>
 *
 * @see ValueReferenceParser
 * @see io.pravah.common.domain.resolution.ValueResolverProvider
 */
@Component
public class ConnectionCredentialResolver {

  private static final String CREDENTIALS_KEY = "credentials";
  private static final String PASSWORD_KEY = "password";

  private final EnvResolverProvider envResolver;
  private final VaultResolverProvider vaultResolver;

  public ConnectionCredentialResolver() {
    this.envResolver = new EnvResolverProvider();
    this.vaultResolver = new VaultResolverProvider();
  }

  /**
   * Validates credential references in the config without resolving them.
   *
   * @param config the full connection config map
   * @throws IllegalArgumentException if credentials structure or reference format is invalid
   */
  @SuppressWarnings("unchecked")
  public void validateCredentials(Map<String, Object> config) {
    if (config == null) {
      return;
    }
    Object credentialsObj = config.get(CREDENTIALS_KEY);
    if (credentialsObj == null) {
      return;
    }
    if (!(credentialsObj instanceof Map)) {
      throw new IllegalArgumentException(
          "config.credentials must be an object, got: "
              + credentialsObj.getClass().getSimpleName());
    }
    Map<String, Object> credentials = (Map<String, Object>) credentialsObj;
    Object passwordRef = credentials.get(PASSWORD_KEY);
    if (passwordRef != null) {
      ValueReferenceParser.validateCredentialRef(passwordRef.toString());
    }
  }

  /**
   * Extracts and resolves the password from config.credentials.password.
   *
   * @param config the full connection config map
   * @return the resolved password, or empty string if no credentials configured
   */
  @SuppressWarnings("unchecked")
  public String resolvePassword(Map<String, Object> config) {
    return resolvePassword(config, null);
  }

  /**
   * Extracts and resolves the password from config.credentials.password with tenant context.
   *
   * @param config the full connection config map
   * @param tenantId the tenant ID for secret lookups (may be null for env-only resolution)
   * @return the resolved password, or empty string if no credentials configured
   */
  @SuppressWarnings("unchecked")
  public String resolvePassword(Map<String, Object> config, UUID tenantId) {
    if (config == null) {
      return "";
    }
    Object credentialsObj = config.get(CREDENTIALS_KEY);
    if (credentialsObj == null) {
      return "";
    }
    if (!(credentialsObj instanceof Map)) {
      throw new IllegalArgumentException(
          "config.credentials must be an object, got: "
              + credentialsObj.getClass().getSimpleName());
    }
    Map<String, Object> credentials = (Map<String, Object>) credentialsObj;
    Object passwordRef = credentials.get(PASSWORD_KEY);
    if (passwordRef == null) {
      return "";
    }
    return resolveReference(passwordRef.toString(), tenantId);
  }

  /**
   * Resolves a credential reference string to its actual value.
   *
   * @param reference the reference string (e.g., "env:VAR_NAME")
   * @return the resolved credential value
   */
  public String resolveReference(String reference) {
    return resolveReference(reference, null);
  }

  /**
   * Resolves a credential reference string to its actual value with tenant context.
   *
   * @param reference the reference string
   * @param tenantId the tenant ID for secret lookups (required for ${secret.name} refs)
   * @return the resolved credential value
   * @throws IllegalStateException if tenantId is null and reference requires tenant context
   */
  public String resolveReference(String reference, UUID tenantId) {
    if (reference == null || reference.isBlank()) {
      return "";
    }

    ValueReference ref = ValueReferenceParser.parseCredentialRef(reference);

    if (ref instanceof EnvRef) {
      ResolutionContext ctx =
          tenantId != null
              ? ResolutionContext.forValidation(tenantId)
              : ResolutionContext.forValidation(
                  UUID.fromString("00000000-0000-0000-0000-000000000000"));
      return (String) envResolver.resolve(ref, ctx);
    }
    if (ref instanceof VaultRef) {
      ResolutionContext ctx =
          tenantId != null
              ? ResolutionContext.forValidation(tenantId)
              : ResolutionContext.forValidation(
                  UUID.fromString("00000000-0000-0000-0000-000000000000"));
      return (String) vaultResolver.resolve(ref, ctx);
    }
    if (ref instanceof SecretRef) {
      if (tenantId == null) {
        throw new IllegalStateException(
            "Tenant context required for secret reference resolution: " + reference);
      }
      throw new UnsupportedOperationException(
          "Secret references (${secret.name}) require SecretResolverProvider. "
              + "Reference: "
              + reference);
    }
    if (ref instanceof LiteralValue) {
      return reference;
    }

    return reference;
  }
}

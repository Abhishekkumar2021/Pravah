package io.pravah.common.domain.resolution;

/**
 * Provider interface for resolving specific types of value references.
 *
 * <p>Implementations handle different reference types:
 *
 * <ul>
 *   <li>{@code VariableResolverProvider} — resolves ${var.name} from context
 *   <li>{@code SecretResolverProvider} — resolves ${secret.name} from tenant_secrets + provider
 *   <li>{@code EnvResolverProvider} — resolves env:VAR from environment
 *   <li>{@code VaultResolverProvider} — resolves vault:path#key from Vault API
 * </ul>
 *
 * <p>Providers are discovered via Spring component scanning and ordered by {@code @Order}.
 */
public interface ValueResolverProvider {

  /**
   * Checks if this provider can resolve the given reference.
   *
   * @param ref the reference to check
   * @return true if this provider handles this reference type
   */
  boolean supports(ValueReference ref);

  /**
   * Resolves the reference to its actual value.
   *
   * @param ref the reference to resolve
   * @param ctx the resolution context
   * @return the resolved value (never null, may be empty string)
   * @throws IllegalArgumentException if resolution fails (missing secret, unset env var, etc.)
   */
  Object resolve(ValueReference ref, ResolutionContext ctx);

  /**
   * Validates that the reference can be resolved without actually resolving it.
   *
   * <p>Used during pipeline publish to catch errors early. Default implementation does nothing.
   *
   * @param ref the reference to validate
   * @param ctx the resolution context (for tenant lookup)
   * @throws IllegalArgumentException if validation fails
   */
  default void validate(ValueReference ref, ResolutionContext ctx) {
    // Default: no validation
  }
}

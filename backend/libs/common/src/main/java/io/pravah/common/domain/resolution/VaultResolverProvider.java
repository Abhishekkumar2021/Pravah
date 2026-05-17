package io.pravah.common.domain.resolution;

/**
 * Resolves {@code vault:path#key} references from HashiCorp Vault.
 *
 * <p>This is a placeholder implementation. Production deployment requires:
 *
 * <ul>
 *   <li>Vault client configuration (address, auth method)
 *   <li>Kubernetes auth or AppRole for service authentication
 *   <li>Policy configuration for secret access
 * </ul>
 */
public class VaultResolverProvider implements ValueResolverProvider {

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof VaultRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    VaultRef vaultRef = (VaultRef) ref;
    throw new UnsupportedOperationException(
        "Vault integration not configured. Reference: %s. "
            + "Use env:VAR_NAME for local development or configure Vault client for production."
                .formatted(vaultRef.raw()));
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Path format validated in VaultRef constructor
    // Actual secret existence requires Vault connectivity
  }
}

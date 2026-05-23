package io.pravah.common.domain.resolution;

import io.pravah.common.vault.VaultException;
import io.pravah.common.vault.VaultKvReader;
import java.util.Objects;

/** Resolves {@code vault:path#key} references from HashiCorp Vault KV v2 (ADR-007). */
public class VaultResolverProvider implements ValueResolverProvider {

  private final VaultKvReader vaultReader;

  /** Uses {@link VaultKvReader#disabled()} until Spring wires a real client. */
  public VaultResolverProvider() {
    this(VaultKvReader.disabled());
  }

  public VaultResolverProvider(VaultKvReader vaultReader) {
    this.vaultReader = Objects.requireNonNull(vaultReader, "vaultReader");
  }

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof VaultRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    VaultRef vaultRef = (VaultRef) ref;
    try {
      return vaultReader.readField(vaultRef.path(), vaultRef.key());
    } catch (VaultException e) {
      throw new IllegalStateException(
          "Failed to resolve Vault reference %s: %s".formatted(vaultRef.raw(), e.getMessage()), e);
    }
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Path format validated in VaultRef constructor; existence checked at resolve time.
  }
}

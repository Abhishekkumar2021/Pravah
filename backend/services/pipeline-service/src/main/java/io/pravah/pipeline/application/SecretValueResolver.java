package io.pravah.pipeline.application;

import io.pravah.common.domain.resolution.EnvRef;
import io.pravah.common.domain.resolution.EnvResolverProvider;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.VaultRef;
import io.pravah.common.domain.resolution.VaultResolverProvider;
import io.pravah.pipeline.infrastructure.persistence.entity.TenantSecretEntity;
import org.springframework.stereotype.Component;

/**
 * Resolves tenant secret metadata to actual secret values via configured providers (env, vault).
 *
 * <p>Shared by {@link SecretResolverProvider} and the internal execution API.
 */
@Component
public class SecretValueResolver {

  private final EnvResolverProvider envResolver = new EnvResolverProvider();
  private final VaultResolverProvider vaultResolver = new VaultResolverProvider();

  public String resolve(TenantSecretEntity secret, ResolutionContext ctx) {
    return switch (secret.getProvider()) {
      case "env" -> {
        EnvRef envRef = new EnvRef(secret.getProviderPath());
        yield (String) envResolver.resolve(envRef, ctx);
      }
      case "vault" -> {
        String[] parts = secret.getProviderPath().split("#", 2);
        if (parts.length != 2) {
          throw new IllegalArgumentException(
              "Invalid vault path for secret '%s': %s"
                  .formatted(secret.getName(), secret.getProviderPath()));
        }
        VaultRef vaultRef = new VaultRef(parts[0], parts[1]);
        yield (String) vaultResolver.resolve(vaultRef, ctx);
      }
      case "aws_sm" ->
          throw new UnsupportedOperationException(
              "AWS Secrets Manager integration not yet implemented. Secret: " + secret.getName());
      default ->
          throw new IllegalArgumentException(
              "Unknown secret provider '%s' for secret '%s'"
                  .formatted(secret.getProvider(), secret.getName()));
    };
  }
}

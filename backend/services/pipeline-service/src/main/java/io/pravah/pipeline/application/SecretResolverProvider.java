package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.resolution.EnvRef;
import io.pravah.common.domain.resolution.EnvResolverProvider;
import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.SecretRef;
import io.pravah.common.domain.resolution.ValueReference;
import io.pravah.common.domain.resolution.ValueResolverProvider;
import io.pravah.common.domain.resolution.VaultRef;
import io.pravah.common.domain.resolution.VaultResolverProvider;
import io.pravah.pipeline.infrastructure.persistence.entity.TenantSecretEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code ${secret.name}} references using the tenant_secrets table.
 *
 * <p>Secret resolution flow:
 *
 * <ol>
 *   <li>Check cache for previously resolved value
 *   <li>Look up secret metadata in tenant_secrets table
 *   <li>Get provider type (env, vault, aws_sm)
 *   <li>Delegate to appropriate provider resolver
 *   <li>Cache resolved value for this execution
 * </ol>
 */
@Component
public class SecretResolverProvider implements ValueResolverProvider {

  private static final Logger log = LoggerFactory.getLogger(SecretResolverProvider.class);

  private final SecretApplicationService secretService;
  private final EnvResolverProvider envResolver;
  private final VaultResolverProvider vaultResolver;

  public SecretResolverProvider(SecretApplicationService secretService) {
    this.secretService = secretService;
    this.envResolver = new EnvResolverProvider();
    this.vaultResolver = new VaultResolverProvider();
  }

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof SecretRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    SecretRef secretRef = (SecretRef) ref;

    String cachedValue = ctx.secretsCache().get(secretRef.name());
    if (cachedValue != null) {
      log.debug("Secret cache hit", kv("secret_name", secretRef.name()));
      return cachedValue;
    }

    log.debug(
        "Resolving secret",
        kv("secret_name", secretRef.name()),
        kv("tenant_id", ctx.tenantId()),
        kv("execution_id", ctx.executionId()));

    TenantSecretEntity secret =
        secretService.getSecretEntityByName(ctx.tenantId(), secretRef.name());

    String value = resolveFromProvider(secret, ctx);
    ctx.secretsCache().put(secretRef.name(), value);
    return value;
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    SecretRef secretRef = (SecretRef) ref;
    secretService.validateSecretReferences(java.util.List.of(secretRef.name()));
  }

  private String resolveFromProvider(TenantSecretEntity secret, ResolutionContext ctx) {
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

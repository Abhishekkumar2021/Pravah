package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.resolution.ResolutionContext;
import io.pravah.common.domain.resolution.SecretRef;
import io.pravah.common.domain.resolution.ValueReference;
import io.pravah.common.domain.resolution.ValueResolverProvider;
import io.pravah.execution.application.port.SecretCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Resolves {@code ${secret.name}} via pipeline-service internal API during stage execution. */
@Component
public class HttpSecretResolverProvider implements ValueResolverProvider {

  private static final Logger log = LoggerFactory.getLogger(HttpSecretResolverProvider.class);

  private final SecretCatalog secretCatalog;

  public HttpSecretResolverProvider(SecretCatalog secretCatalog) {
    this.secretCatalog = secretCatalog;
  }

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof SecretRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    SecretRef secretRef = (SecretRef) ref;
    String cached = ctx.secretsCache().get(secretRef.name());
    if (cached != null) {
      return cached;
    }
    if (ctx.executionId() == null || ctx.executionTime() == null) {
      throw new IllegalStateException(
          "Cannot resolve secrets without execution id and time: " + secretRef.name());
    }

    log.debug(
        "Resolving secret via pipeline-service",
        kv("secret_name", secretRef.name()),
        kv("tenant_id", ctx.tenantId()),
        kv("execution_id", ctx.executionId()));

    String value =
        secretCatalog.resolve(
            ctx.tenantId(), ctx.executionId(), ctx.executionTime(), secretRef.name());
    ctx.secretsCache().put(secretRef.name(), value);
    return value;
  }
}

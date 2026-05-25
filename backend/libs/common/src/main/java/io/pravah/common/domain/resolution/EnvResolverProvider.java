package io.pravah.common.domain.resolution;

/**
 * Resolves {@code env:VAR_NAME} references from system environment variables.
 *
 * <p>Used primarily for credential references in connection configs during local development.
 */
public class EnvResolverProvider implements ValueResolverProvider {

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof EnvRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    EnvRef envRef = (EnvRef) ref;
    String value = System.getenv(envRef.varName());
    if (value == null) {
      throw new IllegalArgumentException(
          "Environment variable '%s' is not set (reference: %s)"
              .formatted(envRef.varName(), envRef.raw()));
    }
    return value;
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Validation only checks format, not availability (env may differ at runtime)
    // Format already validated in EnvRef constructor
  }
}

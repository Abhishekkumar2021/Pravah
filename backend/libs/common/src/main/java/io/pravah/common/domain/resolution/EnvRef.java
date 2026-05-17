package io.pravah.common.domain.resolution;

import java.util.regex.Pattern;

/** Reference to an environment variable: {@code env:VAR_NAME}. Resolved at stage execution. */
public record EnvRef(String varName) implements ValueReference {

  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

  public EnvRef {
    if (varName == null || varName.isBlank()) {
      throw new IllegalArgumentException("Environment variable name is required");
    }
    if (!ENV_VAR_PATTERN.matcher(varName).matches()) {
      throw new IllegalArgumentException(
          "Invalid environment variable name: " + varName + ". Must match [A-Za-z_][A-Za-z0-9_]*");
    }
  }

  @Override
  public String raw() {
    return "env:" + varName;
  }

  @Override
  public boolean isDeferred() {
    return true;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.ENV;
  }
}

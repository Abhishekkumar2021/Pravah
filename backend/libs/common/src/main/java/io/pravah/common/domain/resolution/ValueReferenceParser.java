package io.pravah.common.domain.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses value reference strings into typed {@link ValueReference} objects.
 *
 * <p>Supported formats:
 *
 * <ul>
 *   <li>{@code ${var.name}} — pipeline variable
 *   <li>{@code ${secret.name}} — tenant secret
 *   <li>{@code ${execution_date}} — built-in variable
 *   <li>{@code env:VAR_NAME} — environment variable (credential reference)
 *   <li>{@code vault:path#key} — HashiCorp Vault (future)
 * </ul>
 *
 * <p>All patterns are linear (no backtracking) for ReDoS safety.
 */
public final class ValueReferenceParser {

  private static final Pattern VAR_REF = Pattern.compile("\\$\\{var\\.([a-zA-Z_][a-zA-Z0-9_]*)}");
  private static final Pattern SECRET_REF =
      Pattern.compile("\\$\\{secret\\.([a-zA-Z_][a-zA-Z0-9_]*)}");
  private static final Pattern BUILTIN_REF =
      Pattern.compile("\\$\\{(execution_date|execution_id|pipeline_id|pipeline_version)}");

  private static final Pattern ENV_REF = Pattern.compile("^env:([A-Za-z_][A-Za-z0-9_]*)$");
  private static final Pattern VAULT_REF =
      Pattern.compile("^vault:([^#]+)#([a-zA-Z_][a-zA-Z0-9_]*)$");

  private static final Pattern ANY_INTERPOLATION =
      Pattern.compile(
          "\\$\\{(var\\.([a-zA-Z_][a-zA-Z0-9_]*)|secret\\.([a-zA-Z_][a-zA-Z0-9_]*)|execution_date|execution_id|pipeline_id|pipeline_version)}");

  private ValueReferenceParser() {}

  /**
   * Extracts all value references from a string.
   *
   * @param value the string to parse
   * @return list of references found (may be empty)
   */
  public static List<ValueReference> extractAll(String value) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }

    List<ValueReference> refs = new ArrayList<>();
    Matcher matcher = ANY_INTERPOLATION.matcher(value);

    while (matcher.find()) {
      String full = matcher.group(1);
      if (full.startsWith("var.")) {
        refs.add(new VariableRef(matcher.group(2)));
      } else if (full.startsWith("secret.")) {
        refs.add(new SecretRef(matcher.group(3)));
      } else if (BuiltinRef.SUPPORTED_BUILTINS.contains(full)) {
        refs.add(new BuiltinRef(full));
      }
    }

    return refs;
  }

  /**
   * Extracts all variable references ({@code ${var.name}}) from a string.
   *
   * @param value the string to parse
   * @return list of variable names found
   */
  public static List<String> extractVariableNames(String value) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    Matcher matcher = VAR_REF.matcher(value);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /**
   * Extracts all secret references ({@code ${secret.name}}) from a string.
   *
   * @param value the string to parse
   * @return list of secret names found
   */
  public static List<String> extractSecretNames(String value) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    Matcher matcher = SECRET_REF.matcher(value);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /**
   * Parses a credential reference string (used in connection configs).
   *
   * <p>Supports:
   *
   * <ul>
   *   <li>{@code env:VAR_NAME} — environment variable
   *   <li>{@code vault:path#key} — HashiCorp Vault
   *   <li>{@code ${secret.name}} — tenant secret reference
   * </ul>
   *
   * @param value the reference string
   * @return the parsed reference
   * @throws IllegalArgumentException if the format is not recognized
   */
  public static ValueReference parseCredentialRef(String value) {
    if (value == null || value.isBlank()) {
      return new LiteralValue("");
    }

    Matcher envMatcher = ENV_REF.matcher(value);
    if (envMatcher.matches()) {
      return new EnvRef(envMatcher.group(1));
    }

    Matcher vaultMatcher = VAULT_REF.matcher(value);
    if (vaultMatcher.matches()) {
      return new VaultRef(vaultMatcher.group(1), vaultMatcher.group(2));
    }

    Matcher secretMatcher = SECRET_REF.matcher(value);
    if (secretMatcher.matches()) {
      return new SecretRef(secretMatcher.group(1));
    }

    throw new IllegalArgumentException(
        "Unsupported credential reference format: '"
            + value
            + "'. Supported: env:VAR_NAME, vault:path#key, ${secret.name}");
  }

  /**
   * Validates a credential reference format without resolving.
   *
   * @param value the reference string
   * @throws IllegalArgumentException if the format is invalid
   */
  public static void validateCredentialRef(String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    parseCredentialRef(value);
  }

  /**
   * Checks if a string contains any deferred references (secrets, env, vault).
   *
   * @param value the string to check
   * @return true if deferred references are present
   */
  public static boolean containsDeferredRefs(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    return SECRET_REF.matcher(value).find()
        || ENV_REF.matcher(value).matches()
        || VAULT_REF.matcher(value).matches();
  }

  /**
   * Checks if a string contains any variable references.
   *
   * @param value the string to check
   * @return true if variable references are present
   */
  public static boolean containsVariableRefs(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    return VAR_REF.matcher(value).find();
  }
}

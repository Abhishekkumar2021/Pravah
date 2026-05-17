package io.pravah.common.domain.resolution;

/**
 * Represents a reference to a value that may need resolution.
 *
 * <p>Supported reference types:
 *
 * <ul>
 *   <li>{@link VariableRef} — {@code ${var.name}} pipeline variable
 *   <li>{@link SecretRef} — {@code ${secret.name}} tenant secret
 *   <li>{@link BuiltinRef} — {@code ${execution_date}} etc.
 *   <li>{@link EnvRef} — {@code env:VAR_NAME} environment variable
 *   <li>{@link VaultRef} — {@code vault:path#key} HashiCorp Vault (future)
 *   <li>{@link StageOutputRef} — {@code ${stages.stageId.output.key}} upstream stage output
 *   <li>{@link LiteralValue} — plain value, no resolution needed
 * </ul>
 *
 * @see ValueReferenceParser
 */
public sealed interface ValueReference
    permits VariableRef, SecretRef, BuiltinRef, EnvRef, VaultRef, StageOutputRef, LiteralValue {

  /** The original string representation of this reference. */
  String raw();

  /**
   * Whether this reference is resolved at stage execution time (deferred) or earlier.
   *
   * <ul>
   *   <li>Non-deferred (variables, builtins): resolved at execution start, stored in snapshot
   *   <li>Deferred (secrets, env, vault): resolved just before stage execution
   * </ul>
   */
  boolean isDeferred();

  /** The reference type for logging and provider matching. */
  ReferenceType type();

  enum ReferenceType {
    VARIABLE,
    SECRET,
    BUILTIN,
    ENV,
    VAULT,
    STAGE_OUTPUT,
    LITERAL
  }
}

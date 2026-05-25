package io.pravah.common.domain.resolution;

/**
 * Resolves {@code ${var.name}} references from the variable context.
 *
 * <p>Variables are resolved at execution start and their values are stored in the resolution
 * context's variableContext map.
 */
public class VariableResolverProvider implements ValueResolverProvider {

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof VariableRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    VariableRef varRef = (VariableRef) ref;
    Object value = ctx.variableContext().get(varRef.name());
    if (value == null && !ctx.variableContext().containsKey(varRef.name())) {
      throw new IllegalArgumentException("Undefined variable: " + varRef.name());
    }
    return value;
  }

  @Override
  public void validate(ValueReference ref, ResolutionContext ctx) {
    // Variable validation happens during variable resolution phase, not here
  }
}

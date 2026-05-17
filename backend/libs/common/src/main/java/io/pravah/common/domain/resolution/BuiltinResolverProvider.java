package io.pravah.common.domain.resolution;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Resolves built-in variables: {@code ${execution_date}}, {@code ${execution_id}}, etc.
 *
 * <p>Built-in variables are resolved at execution start from the resolution context.
 */
public class BuiltinResolverProvider implements ValueResolverProvider {

  private static final DateTimeFormatter EXECUTION_DATE_FORMAT =
      DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

  @Override
  public boolean supports(ValueReference ref) {
    return ref instanceof BuiltinRef;
  }

  @Override
  public Object resolve(ValueReference ref, ResolutionContext ctx) {
    BuiltinRef builtin = (BuiltinRef) ref;
    return switch (builtin.name()) {
      case "execution_date" -> {
        if (ctx.executionTime() == null) {
          yield "";
        }
        yield EXECUTION_DATE_FORMAT.format(ctx.executionTime());
      }
      case "execution_id" -> ctx.executionId() != null ? ctx.executionId().toString() : "";
      case "pipeline_id" -> {
        Object val = ctx.variableContext().get("__pipeline_id");
        yield val != null ? val.toString() : "";
      }
      case "pipeline_version" -> {
        Object val = ctx.variableContext().get("__pipeline_version");
        yield val != null ? val.toString() : "";
      }
      default -> throw new IllegalArgumentException("Unknown builtin: " + builtin.name());
    };
  }
}

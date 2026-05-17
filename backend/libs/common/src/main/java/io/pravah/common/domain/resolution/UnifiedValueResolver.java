package io.pravah.common.domain.resolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified resolver for all value reference types.
 *
 * <p>Handles both string interpolation ({@code "Hello ${var.name}"}) and standalone references.
 * Delegates to registered {@link ValueResolverProvider} implementations.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * UnifiedValueResolver resolver = new UnifiedValueResolver(providers);
 * Object value = resolver.resolve("${var.batch_size}", context);
 * Map<String, Object> config = resolver.resolveConfig(stageConfig, context);
 * }</pre>
 */
public class UnifiedValueResolver {

  private static final Pattern INTERPOLATION =
      Pattern.compile(
          "\\$\\{(var\\.([a-zA-Z_][a-zA-Z0-9_]*)|secret\\.([a-zA-Z_][a-zA-Z0-9_]*)|stages\\.([a-zA-Z_][a-zA-Z0-9_-]*)\\.output\\.([a-zA-Z_][a-zA-Z0-9_.]*)|execution_date|execution_id|pipeline_id|pipeline_version)}");

  private final List<ValueResolverProvider> providers;

  public UnifiedValueResolver(List<ValueResolverProvider> providers) {
    this.providers = providers != null ? new ArrayList<>(providers) : List.of();
  }

  /**
   * Resolves a single value, handling both interpolation and standalone references.
   *
   * @param value the value to resolve (may contain references)
   * @param ctx the resolution context
   * @return the resolved value
   */
  public Object resolve(Object value, ResolutionContext ctx) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return resolveString(s, ctx);
    }
    if (value instanceof Map<?, ?> map) {
      return resolveMap(map, ctx);
    }
    if (value instanceof List<?> list) {
      return resolveList(list, ctx);
    }
    return value;
  }

  /**
   * Resolves all references in a configuration map (deep recursive).
   *
   * @param config the configuration map
   * @param ctx the resolution context
   * @return new map with resolved values
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> resolveConfig(Map<String, Object> config, ResolutionContext ctx) {
    if (config == null || config.isEmpty()) {
      return config == null ? Map.of() : config;
    }
    return (Map<String, Object>) resolve(config, ctx);
  }

  private Object resolveString(String value, ResolutionContext ctx) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    Matcher matcher = INTERPOLATION.matcher(value);
    if (!matcher.find()) {
      return value;
    }

    matcher.reset();
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String fullMatch = matcher.group(1);
      ValueReference ref = parseInterpolation(fullMatch, matcher);
      Object resolved = resolveReference(ref, ctx);
      matcher.appendReplacement(result, Matcher.quoteReplacement(stringValue(resolved)));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  private ValueReference parseInterpolation(String fullMatch, Matcher matcher) {
    if (fullMatch.startsWith("var.")) {
      return new VariableRef(matcher.group(2));
    }
    if (fullMatch.startsWith("secret.")) {
      return new SecretRef(matcher.group(3));
    }
    if (fullMatch.startsWith("stages.")) {
      return new StageOutputRef(matcher.group(4), matcher.group(5));
    }
    if (BuiltinRef.SUPPORTED_BUILTINS.contains(fullMatch)) {
      return new BuiltinRef(fullMatch);
    }
    throw new IllegalArgumentException("Unknown interpolation: ${" + fullMatch + "}");
  }

  private Object resolveReference(ValueReference ref, ResolutionContext ctx) {
    for (ValueResolverProvider provider : providers) {
      if (provider.supports(ref)) {
        return provider.resolve(ref, ctx);
      }
    }
    throw new IllegalArgumentException("No provider found for reference: " + ref.raw());
  }

  private Map<String, Object> resolveMap(Map<?, ?> map, ResolutionContext ctx) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = entry.getKey().toString();
      result.put(key, resolve(entry.getValue(), ctx));
    }
    return result;
  }

  private List<Object> resolveList(List<?> list, ResolutionContext ctx) {
    List<Object> result = new ArrayList<>(list.size());
    for (Object item : list) {
      result.add(resolve(item, ctx));
    }
    return result;
  }

  private static String stringValue(Object value) {
    return value != null ? value.toString() : "";
  }

  /**
   * Validates all references in a value without resolving them.
   *
   * @param value the value to validate
   * @param ctx the resolution context
   * @throws IllegalArgumentException if validation fails
   */
  public void validateReferences(Object value, ResolutionContext ctx) {
    if (value == null) {
      return;
    }
    if (value instanceof String s) {
      validateStringReferences(s, ctx);
    } else if (value instanceof Map<?, ?> map) {
      for (Object v : map.values()) {
        validateReferences(v, ctx);
      }
    } else if (value instanceof List<?> list) {
      for (Object item : list) {
        validateReferences(item, ctx);
      }
    }
  }

  private void validateStringReferences(String value, ResolutionContext ctx) {
    List<ValueReference> refs = ValueReferenceParser.extractAll(value);
    for (ValueReference ref : refs) {
      for (ValueResolverProvider provider : providers) {
        if (provider.supports(ref)) {
          provider.validate(ref, ctx);
          break;
        }
      }
    }
  }
}

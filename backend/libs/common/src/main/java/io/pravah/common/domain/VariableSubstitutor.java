package io.pravah.common.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substitutes {@code ${var.name}} references in pipeline definition values (US-01.05). */
public final class VariableSubstitutor {

  private static final Pattern VAR_REFERENCE =
      Pattern.compile("\\$\\{var\\.([a-zA-Z_][a-zA-Z0-9_]*)}");

  private VariableSubstitutor() {}

  public static Map<String, Object> substitute(
      Map<String, Object> definition, Map<String, Object> context) {
    if (definition == null) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> copy = (Map<String, Object>) substituteValue(definition, context);
    return copy;
  }

  private static Object substituteValue(Object node, Map<String, Object> context) {
    if (node instanceof String s) {
      return substituteString(s, context);
    }
    if (node instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = entry.getKey().toString();
        if ("variables".equals(key) || "environments".equals(key)) {
          out.put(key, entry.getValue());
        } else {
          out.put(key, substituteValue(entry.getValue(), context));
        }
      }
      return out;
    }
    if (node instanceof List<?> list) {
      List<Object> out = new ArrayList<>(list.size());
      for (Object item : list) {
        out.add(substituteValue(item, context));
      }
      return out;
    }
    return node;
  }

  private static String substituteString(String template, Map<String, Object> context) {
    Matcher matcher = VAR_REFERENCE.matcher(template);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!context.containsKey(name)) {
        throw new IllegalArgumentException("undefined variable reference: var." + name);
      }
      Object value = context.get(name);
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(value != null ? value.toString() : ""));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}

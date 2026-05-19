package io.pravah.connect.domain;

import java.util.List;

/**
 * Defines a configuration field for a connector. Used to generate dynamic configuration forms in
 * the UI.
 */
public record ConfigField(
    String name,
    String label,
    String description,
    FieldType type,
    boolean required,
    Object defaultValue,
    List<String> options,
    String placeholder,
    String group,
    int order,
    String dependsOn,
    String condition) {
  public enum FieldType {
    STRING,
    PASSWORD,
    NUMBER,
    BOOLEAN,
    SELECT,
    MULTI_SELECT,
    TEXTAREA,
    FILE,
    JSON,
    KEY_VALUE,
    CRON,
    URL
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  public static class Builder {
    private final String name;
    private String label;
    private String description = "";
    private FieldType type = FieldType.STRING;
    private boolean required = false;
    private Object defaultValue;
    private List<String> options;
    private String placeholder;
    private String group = "General";
    private int order = 0;
    private String dependsOn;
    private String condition;

    private Builder(String name) {
      this.name = name;
      this.label = name;
    }

    public Builder label(String label) {
      this.label = label;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder type(FieldType type) {
      this.type = type;
      return this;
    }

    public Builder required() {
      this.required = true;
      return this;
    }

    public Builder defaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder options(List<String> options) {
      this.options = options;
      return this;
    }

    public Builder placeholder(String placeholder) {
      this.placeholder = placeholder;
      return this;
    }

    public Builder group(String group) {
      this.group = group;
      return this;
    }

    public Builder order(int order) {
      this.order = order;
      return this;
    }

    public Builder dependsOn(String dependsOn, String condition) {
      this.dependsOn = dependsOn;
      this.condition = condition;
      return this;
    }

    public ConfigField build() {
      return new ConfigField(
          name,
          label,
          description,
          type,
          required,
          defaultValue,
          options,
          placeholder,
          group,
          order,
          dependsOn,
          condition);
    }
  }
}

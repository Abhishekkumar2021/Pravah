package io.pravah.connect.domain;

import java.util.List;
import java.util.Map;

/**
 * Specification for a connector type. Describes the connector's capabilities, configuration schema,
 * and metadata.
 */
public record ConnectorSpec(
    String id,
    String name,
    String description,
    String icon,
    String category,
    ConnectorType type,
    ConnectorMode mode,
    String version,
    List<ConfigField> configFields,
    Map<String, Object> capabilities,
    List<String> tags) {
  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static class Builder {
    private final String id;
    private String name;
    private String description = "";
    private String icon;
    private String category;
    private ConnectorType type;
    private ConnectorMode mode = ConnectorMode.SOURCE;
    private String version = "1.0.0";
    private List<ConfigField> configFields = List.of();
    private Map<String, Object> capabilities = Map.of();
    private List<String> tags = List.of();

    private Builder(String id) {
      this.id = id;
      this.name = id;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder icon(String icon) {
      this.icon = icon;
      return this;
    }

    public Builder category(String category) {
      this.category = category;
      return this;
    }

    public Builder type(ConnectorType type) {
      this.type = type;
      return this;
    }

    public Builder mode(ConnectorMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public Builder configFields(List<ConfigField> configFields) {
      this.configFields = configFields;
      return this;
    }

    public Builder capabilities(Map<String, Object> capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    public Builder tags(List<String> tags) {
      this.tags = tags;
      return this;
    }

    public ConnectorSpec build() {
      return new ConnectorSpec(
          id,
          name,
          description,
          icon,
          category,
          type,
          mode,
          version,
          configFields,
          capabilities,
          tags);
    }
  }
}

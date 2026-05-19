package io.pravah.connect.connector.database;

import io.pravah.connect.domain.ConfigField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Snowflake data warehouse connector (source and sink). */
@Component
public class SnowflakeConnector extends AbstractJdbcConnector {

  @Override
  protected String getDriverClassName() {
    return "net.snowflake.client.jdbc.SnowflakeDriver";
  }

  @Override
  protected String getDefaultPort() {
    return "443";
  }

  @Override
  protected String getConnectorId() {
    return "snowflake";
  }

  @Override
  protected String getDisplayName() {
    return "Snowflake";
  }

  @Override
  protected String getDescription() {
    return "Read and write data in Snowflake data warehouse";
  }

  @Override
  protected String getIconName() {
    return "snowflake";
  }

  @Override
  protected List<String> getTags() {
    return List.of("snowflake", "warehouse", "cloud", "analytics");
  }

  @Override
  protected List<ConfigField> getConfigFields() {
    List<ConfigField> fields = new ArrayList<>(super.getConfigFields());
    fields.add(
        ConfigField.builder("account")
            .label("Account Identifier")
            .description("Snowflake account (e.g. xy12345.us-east-1)")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .group("Connection")
            .order(0)
            .build());
    fields.add(
        ConfigField.builder("warehouse")
            .label("Warehouse")
            .description("Snowflake warehouse name")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .group("Connection")
            .order(10)
            .build());
    fields.add(
        ConfigField.builder("role")
            .label("Role")
            .description("Snowflake role (optional)")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Connection")
            .order(11)
            .build());
    return fields;
  }

  @Override
  protected String buildJdbcUrl(Map<String, Object> config) {
    String account = (String) config.get("account");
    String database = (String) config.get("database");
    String schema = (String) config.getOrDefault("schema", "PUBLIC");
    String warehouse = (String) config.get("warehouse");
    String role = (String) config.get("role");

    StringBuilder url =
        new StringBuilder("jdbc:snowflake://").append(account).append(".snowflakecomputing.com/");
    url.append("?db=").append(database);
    url.append("&schema=").append(schema);
    url.append("&warehouse=").append(warehouse);
    if (role != null && !role.isBlank()) {
      url.append("&role=").append(role);
    }
    return url.toString();
  }
}

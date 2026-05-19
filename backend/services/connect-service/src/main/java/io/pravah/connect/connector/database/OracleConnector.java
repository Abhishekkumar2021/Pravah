package io.pravah.connect.connector.database;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import io.pravah.connect.domain.ConfigField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Oracle database connector. */
@Component
public class OracleConnector extends AbstractJdbcConnector {

  @Override
  protected String getConnectorId() {
    return "oracle";
  }

  @Override
  protected String getDriverClassName() {
    return "oracle.jdbc.OracleDriver";
  }

  @Override
  protected String getDefaultPort() {
    return "1521";
  }

  @Override
  protected String getIconName() {
    return "oracle";
  }

  @Override
  protected String getDisplayName() {
    return "Oracle";
  }

  @Override
  protected String getDescription() {
    return "Oracle Database - enterprise-grade relational database with advanced features.";
  }

  @Override
  protected List<String> getTags() {
    return List.of("database", "sql", "relational", "oracle", "enterprise");
  }

  @Override
  protected boolean supportsCdc() {
    return true; // Via LogMiner
  }

  @Override
  protected List<ConfigField> getConfigFields() {
    List<ConfigField> fields = new ArrayList<>(super.getConfigFields());
    fields.add(
        ConfigField.builder("serviceName")
            .label("Service Name")
            .description("Oracle service name (alternative to SID)")
            .type(STRING)
            .group("Connection")
            .order(4)
            .build());
    fields.add(
        ConfigField.builder("sid")
            .label("SID")
            .description("Oracle SID (alternative to Service Name)")
            .type(STRING)
            .group("Connection")
            .order(5)
            .build());
    return fields;
  }

  @Override
  protected String buildJdbcUrl(Map<String, Object> config) {
    String host = getString(config, "host");
    int port = getInt(config, "port", 1521);
    String serviceName = getString(config, "serviceName");
    String sid = getString(config, "sid");

    if (serviceName != null && !serviceName.isEmpty()) {
      return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + serviceName;
    } else if (sid != null && !sid.isEmpty()) {
      return "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid;
    } else {
      throw new IllegalArgumentException("Either serviceName or sid must be provided");
    }
  }
}

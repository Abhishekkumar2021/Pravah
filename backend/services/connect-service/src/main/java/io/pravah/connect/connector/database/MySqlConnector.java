package io.pravah.connect.connector.database;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** MySQL database connector. */
@Component
public class MySqlConnector extends AbstractJdbcConnector {

  @Override
  protected String getConnectorId() {
    return "mysql";
  }

  @Override
  protected String getDriverClassName() {
    return "com.mysql.cj.jdbc.Driver";
  }

  @Override
  protected String getDefaultPort() {
    return "3306";
  }

  @Override
  protected String getIconName() {
    return "mysql";
  }

  @Override
  protected String getDisplayName() {
    return "MySQL";
  }

  @Override
  protected String getDescription() {
    return "Popular open-source relational database, widely used for web applications.";
  }

  @Override
  protected List<String> getTags() {
    return List.of("database", "sql", "relational", "mysql", "open-source");
  }

  @Override
  protected boolean supportsCdc() {
    return true; // Via binlog
  }

  @Override
  protected String buildJdbcUrl(Map<String, Object> config) {
    String host = getString(config, "host");
    int port = getInt(config, "port", 3306);
    String database = getString(config, "database");
    boolean ssl = getBoolean(config, "ssl", false);

    StringBuilder url = new StringBuilder();
    url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
    url.append("?useSSL=").append(ssl);
    url.append("&allowPublicKeyRetrieval=true");
    url.append("&serverTimezone=UTC");

    return url.toString();
  }
}

package io.pravah.connect.connector.database;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** PostgreSQL database connector. */
@Component
public class PostgresConnector extends AbstractJdbcConnector {

  @Override
  protected String getConnectorId() {
    return "postgres";
  }

  @Override
  protected String getDriverClassName() {
    return "org.postgresql.Driver";
  }

  @Override
  protected String getDefaultPort() {
    return "5432";
  }

  @Override
  protected String getIconName() {
    return "postgres";
  }

  @Override
  protected String getDisplayName() {
    return "PostgreSQL";
  }

  @Override
  protected String getDescription() {
    return "Open-source relational database with advanced features including JSON support, full-text search, and extensions.";
  }

  @Override
  protected List<String> getTags() {
    return List.of("database", "sql", "relational", "postgres", "postgresql", "open-source");
  }

  @Override
  protected boolean supportsCdc() {
    return true; // Via logical replication
  }

  @Override
  protected String buildJdbcUrl(Map<String, Object> config) {
    String host = getString(config, "host");
    int port = getInt(config, "port", 5432);
    String database = getString(config, "database");
    boolean ssl = getBoolean(config, "ssl", false);

    StringBuilder url = new StringBuilder();
    url.append("jdbc:postgresql://")
        .append(host)
        .append(":")
        .append(port)
        .append("/")
        .append(database);

    if (ssl) {
      url.append("?sslmode=require");
    }

    return url.toString();
  }
}

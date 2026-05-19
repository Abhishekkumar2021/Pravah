package io.pravah.connect.connector.database;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.sql.*;
import java.util.*;

/**
 * Base class for JDBC-based database connectors. Provides common functionality for PostgreSQL,
 * MySQL, SQL Server, Oracle, etc.
 */
public abstract class AbstractJdbcConnector implements SourceConnector, SinkConnector {

  protected abstract String getDriverClassName();

  protected abstract String getDefaultPort();

  protected abstract String buildJdbcUrl(Map<String, Object> config);

  protected abstract String getIconName();

  protected abstract String getDisplayName();

  protected abstract String getDescription();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder(getConnectorId())
        .name(getDisplayName())
        .description(getDescription())
        .icon(getIconName())
        .category("Database")
        .type(ConnectorType.DATABASE)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "discover", true,
                "incremental", true,
                "fullRefresh", true,
                "cdc", supportsCdc()))
        .tags(getTags())
        .build();
  }

  protected abstract String getConnectorId();

  protected List<String> getTags() {
    return List.of("database", "sql", "relational");
  }

  protected boolean supportsCdc() {
    return false;
  }

  protected List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder("host")
            .label("Host")
            .description("Database server hostname or IP address")
            .type(STRING)
            .required()
            .placeholder("localhost")
            .group("Connection")
            .order(1)
            .build(),
        ConfigField.builder("port")
            .label("Port")
            .description("Database server port")
            .type(NUMBER)
            .required()
            .defaultValue(Integer.parseInt(getDefaultPort()))
            .group("Connection")
            .order(2)
            .build(),
        ConfigField.builder("database")
            .label("Database")
            .description("Database name")
            .type(STRING)
            .required()
            .group("Connection")
            .order(3)
            .build(),
        ConfigField.builder("username")
            .label("Username")
            .description("Database username")
            .type(STRING)
            .required()
            .group("Authentication")
            .order(4)
            .build(),
        ConfigField.builder("password")
            .label("Password")
            .description("Database password")
            .type(PASSWORD)
            .required()
            .group("Authentication")
            .order(5)
            .build(),
        ConfigField.builder("schema")
            .label("Schema")
            .description("Default schema (optional)")
            .type(STRING)
            .group("Connection")
            .order(6)
            .build(),
        ConfigField.builder("ssl")
            .label("Use SSL")
            .description("Enable SSL/TLS connection")
            .type(BOOLEAN)
            .defaultValue(false)
            .group("Security")
            .order(7)
            .build(),
        ConfigField.builder("connectionOptions")
            .label("Connection Options")
            .description("Additional JDBC connection parameters")
            .type(KEY_VALUE)
            .group("Advanced")
            .order(8)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    if (isBlank(config.get("host"))) {
      errors.put("host", "Host is required");
    }
    if (config.get("port") == null) {
      errors.put("port", "Port is required");
    }
    if (isBlank(config.get("database"))) {
      errors.put("database", "Database name is required");
    }
    if (isBlank(config.get("username"))) {
      errors.put("username", "Username is required");
    }
    if (isBlank(config.get("password"))) {
      errors.put("password", "Password is required");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    String url = buildJdbcUrl(config);
    String username = getString(config, "username");
    String password = getString(config, "password");

    try {
      Class.forName(getDriverClassName());
    } catch (ClassNotFoundException e) {
      return TestResult.failure("JDBC driver not found: " + getDriverClassName());
    }

    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      DatabaseMetaData meta = conn.getMetaData();
      long latency = System.currentTimeMillis() - start;

      return TestResult.success(
          "Connected to " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion(),
          latency,
          Map.of(
              "productName", meta.getDatabaseProductName(),
              "productVersion", meta.getDatabaseProductVersion(),
              "driverName", meta.getDriverName(),
              "driverVersion", meta.getDriverVersion()));
    } catch (SQLException e) {
      return TestResult.failure("Connection failed", e);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();
    String url = buildJdbcUrl(config);
    String username = getString(config, "username");
    String password = getString(config, "password");
    String schema = getString(config, "schema");

    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      DatabaseMetaData meta = conn.getMetaData();
      String catalog = conn.getCatalog();

      try (ResultSet tables =
          meta.getTables(catalog, schema, "%", new String[] {"TABLE", "VIEW"})) {
        while (tables.next()) {
          String tableName = tables.getString("TABLE_NAME");
          String tableSchema = tables.getString("TABLE_SCHEM");
          String tableType = tables.getString("TABLE_TYPE");

          List<FieldInfo> fields = discoverFields(meta, catalog, tableSchema, tableName);
          List<String> primaryKeys = discoverPrimaryKeys(meta, catalog, tableSchema, tableName);

          streams.add(
              new StreamInfo(
                  tableName, tableSchema, fields, primaryKeys, Map.of("tableType", tableType)));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to discover streams", e);
    }

    return streams;
  }

  private List<FieldInfo> discoverFields(
      DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
    List<FieldInfo> fields = new ArrayList<>();
    try (ResultSet columns = meta.getColumns(catalog, schema, table, "%")) {
      while (columns.next()) {
        fields.add(
            new FieldInfo(
                columns.getString("COLUMN_NAME"),
                columns.getString("TYPE_NAME"),
                columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                columns.getString("REMARKS")));
      }
    }
    return fields;
  }

  private List<String> discoverPrimaryKeys(
      DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
    List<String> pks = new ArrayList<>();
    try (ResultSet keys = meta.getPrimaryKeys(catalog, schema, table)) {
      while (keys.next()) {
        pks.add(keys.getString("COLUMN_NAME"));
      }
    }
    return pks;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    String url = buildJdbcUrl(config);
    String username = getString(config, "username");
    String password = getString(config, "password");
    String schema = getString(config, "schema");

    try {
      Connection conn = DriverManager.getConnection(url, username, password);
      String fullTableName =
          schema != null && !schema.isEmpty() ? schema + "." + streamName : streamName;

      String sql = buildSelectQuery(fullTableName, options);
      PreparedStatement stmt =
          conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      stmt.setFetchSize(options.batchSize());

      ResultSet rs = stmt.executeQuery();
      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();
      List<String> columnNames = new ArrayList<>();
      for (int i = 1; i <= columnCount; i++) {
        columnNames.add(meta.getColumnName(i));
      }

      return new JdbcRecordIterator(conn, stmt, rs, columnNames);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to read from " + streamName, e);
    }
  }

  protected String buildSelectQuery(String tableName, ReadOptions options) {
    StringBuilder sql = new StringBuilder("SELECT ");

    if (options.selectedFields().isEmpty()) {
      sql.append("*");
    } else {
      sql.append(String.join(", ", options.selectedFields()));
    }

    sql.append(" FROM ").append(tableName);

    if (options.cursor() != null && !options.cursor().isEmpty()) {
      sql.append(" WHERE _cursor > ").append(options.cursor());
    }

    return sql.toString();
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    String url = buildJdbcUrl(config);
    String username = getString(config, "username");
    String password = getString(config, "password");
    String schema = getString(config, "schema");

    long start = System.currentTimeMillis();
    String fullTableName =
        schema != null && !schema.isEmpty() ? schema + "." + streamName : streamName;

    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      conn.setAutoCommit(false);

      Record sample = records.get(0);
      List<String> columns = new ArrayList<>(sample.data().keySet());

      String insertSql = buildInsertSql(fullTableName, columns, options);

      try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
        int batchCount = 0;
        for (Record record : records) {
          setParameters(stmt, columns, record.data());
          stmt.addBatch();
          batchCount++;

          if (batchCount >= options.batchSize()) {
            stmt.executeBatch();
            batchCount = 0;
          }
        }

        if (batchCount > 0) {
          stmt.executeBatch();
        }

        conn.commit();
      }

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), 0, duration, Map.of());

    } catch (SQLException e) {
      throw new RuntimeException("Failed to write to " + streamName, e);
    }
  }

  protected String buildInsertSql(String tableName, List<String> columns, WriteOptions options) {
    String columnList = String.join(", ", columns);
    String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
    return "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";
  }

  private void setParameters(PreparedStatement stmt, List<String> columns, Map<String, Object> data)
      throws SQLException {
    for (int i = 0; i < columns.size(); i++) {
      stmt.setObject(i + 1, data.get(columns.get(i)));
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // Default implementation does nothing - override in subclasses for DDL support
  }

  protected static boolean isBlank(Object value) {
    return value == null || value.toString().isBlank();
  }

  protected static String getString(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value != null ? value.toString() : null;
  }

  protected static int getInt(Map<String, Object> config, String key, int defaultValue) {
    Object value = config.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Number n) return n.intValue();
    return Integer.parseInt(value.toString());
  }

  protected static boolean getBoolean(
      Map<String, Object> config, String key, boolean defaultValue) {
    Object value = config.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Boolean b) return b;
    return Boolean.parseBoolean(value.toString());
  }

  /** JDBC-based record iterator with auto-close support. */
  private static class JdbcRecordIterator implements RecordIterator {
    private final Connection conn;
    private final PreparedStatement stmt;
    private final ResultSet rs;
    private final List<String> columnNames;
    private long rowNum = 0;
    private boolean hasNext;

    JdbcRecordIterator(
        Connection conn, PreparedStatement stmt, ResultSet rs, List<String> columnNames)
        throws SQLException {
      this.conn = conn;
      this.stmt = stmt;
      this.rs = rs;
      this.columnNames = columnNames;
      this.hasNext = rs.next();
    }

    @Override
    public boolean hasNext() {
      return hasNext;
    }

    @Override
    public Record next() {
      if (!hasNext) {
        throw new NoSuchElementException();
      }

      try {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String col : columnNames) {
          data.put(col, rs.getObject(col));
        }

        rowNum++;
        hasNext = rs.next();

        return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
      } catch (SQLException e) {
        throw new RuntimeException("Error reading row", e);
      }
    }

    @Override
    public String getCursor() {
      return String.valueOf(rowNum);
    }

    @Override
    public void close() {
      try {
        rs.close();
      } catch (SQLException ignored) {
      } finally {
        try {
          stmt.close();
        } catch (SQLException ignored) {
        } finally {
          try {
            conn.close();
          } catch (SQLException ignored) {
          }
        }
      }
    }
  }
}

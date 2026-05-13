package io.pravah.playground.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-process DuckDB service demonstrating ADR-023: DuckDB for runner transforms.
 * 
 * Key capabilities:
 * - Read from CSV, Parquet, JSON
 * - Execute analytical SQL (aggregations, window functions, joins)
 * - Write to Parquet
 * - Larger-than-memory processing via spill-to-disk
 */
@Service
public class DuckDBService {

    private static final Logger log = LoggerFactory.getLogger(DuckDBService.class);

    @Value("${duckdb.memory-limit:512MB}")
    private String memoryLimit;

    @Value("${duckdb.temp-directory:/tmp/duckdb-spill}")
    private String tempDirectory;

    /**
     * Execute a transform: load source data, run SQL, return result.
     * This mirrors what the Pravah runner does for DUCKDB_TRANSFORM jobs.
     */
    public TransformResult executeTransform(String sourcePath, String transformSql) throws SQLException {
        try (Connection conn = createConnection()) {
            loadSourceData(conn, sourcePath);
            executeTransformSql(conn, transformSql);
            return extractResult(conn);
        }
    }

    /**
     * Read a Parquet file directly and return its contents.
     */
    public List<Map<String, Object>> readParquet(String parquetPath) throws SQLException {
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM read_parquet('" + parquetPath + "')");
            return resultSetToList(rs);
        }
    }

    /**
     * Read a CSV file directly.
     */
    public List<Map<String, Object>> readCsv(String csvPath) throws SQLException {
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM read_csv('" + csvPath + "', header=true)");
            return resultSetToList(rs);
        }
    }

    /**
     * Execute arbitrary SQL and return results.
     */
    public List<Map<String, Object>> query(String sql) throws SQLException {
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            return resultSetToList(rs);
        }
    }

    /**
     * Write query result to Parquet file.
     */
    public void writeToParquet(String sql, String outputPath) throws SQLException {
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("COPY (" + sql + ") TO '" + outputPath + "' (FORMAT PARQUET, COMPRESSION ZSTD)");
            log.info("Wrote Parquet to {}", outputPath);
        }
    }

    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET memory_limit = '" + memoryLimit + "'");
            stmt.execute("SET temp_directory = '" + tempDirectory + "'");
        }
        log.debug("Created DuckDB connection (memory_limit={}, temp_dir={})", memoryLimit, tempDirectory);
        return conn;
    }

    private void loadSourceData(Connection conn, String sourcePath) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String extension = sourcePath.substring(sourcePath.lastIndexOf('.') + 1).toLowerCase();
            String readFunction = switch (extension) {
                case "parquet" -> "read_parquet";
                case "csv" -> "read_csv";
                case "json" -> "read_json";
                default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
            };
            String sql = "CREATE TABLE input AS SELECT * FROM " + readFunction + "('" + sourcePath + "')";
            stmt.execute(sql);
            log.info("Loaded source data from {} into 'input' table", sourcePath);
        }
    }

    private void executeTransformSql(Connection conn, String transformSql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE output AS " + transformSql);
            log.info("Executed transform SQL");
        }
    }

    private TransformResult extractResult(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM output");
            List<Map<String, Object>> rows = resultSetToList(rs);

            rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM output");
            rs.next();
            long rowCount = rs.getLong("cnt");

            return new TransformResult(rows, rowCount);
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    public record TransformResult(List<Map<String, Object>> rows, long rowCount) {}
}

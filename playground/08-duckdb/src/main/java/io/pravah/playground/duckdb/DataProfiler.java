package io.pravah.playground.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Profile data using DuckDB's built-in SUMMARIZE function.
 * Pravah runners generate profiles for every transform output.
 */
@Service
public class DataProfiler {

    private static final Logger log = LoggerFactory.getLogger(DataProfiler.class);

    /**
     * Generate a data profile for a Parquet file.
     * Returns statistics: column name, type, min, max, nulls, distinct count.
     */
    public DataProfile profile(String parquetPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            long rowCount = getRowCount(stmt, parquetPath);
            List<ColumnStats> columns = getColumnStats(stmt, parquetPath);

            return new DataProfile(parquetPath, rowCount, columns);
        }
    }

    private long getRowCount(Statement stmt, String path) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM read_parquet('" + path + "')");
        rs.next();
        return rs.getLong("cnt");
    }

    private List<ColumnStats> getColumnStats(Statement stmt, String path) throws SQLException {
        List<ColumnStats> stats = new ArrayList<>();

        ResultSet rs = stmt.executeQuery("SUMMARIZE SELECT * FROM read_parquet('" + path + "')");
        while (rs.next()) {
            stats.add(new ColumnStats(
                    rs.getString("column_name"),
                    rs.getString("column_type"),
                    rs.getString("min"),
                    rs.getString("max"),
                    rs.getLong("null_percentage"),
                    rs.getLong("approx_unique")
            ));
        }
        return stats;
    }

    public record DataProfile(String path, long rowCount, List<ColumnStats> columns) {}

    public record ColumnStats(
            String name,
            String type,
            String min,
            String max,
            long nullPercentage,
            long approxUnique
    ) {}
}

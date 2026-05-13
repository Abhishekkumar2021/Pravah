package io.pravah.playground.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for DuckDB in-process transforms (ADR-023).
 */
@SpringBootTest
class DuckDBIT {

    @Autowired
    DuckDBService duckdb;

    @Autowired
    DataProfiler profiler;

    @TempDir
    Path tempDir;

    /**
     * Read a CSV file directly using DuckDB's read_csv().
     */
    @Test
    void readCsvDirectly() throws Exception {
        String csvPath = getClass().getClassLoader().getResource("data/orders.csv").getPath();

        List<Map<String, Object>> rows = duckdb.readCsv(csvPath);

        assertThat(rows).hasSize(10);
        assertThat(rows.get(0)).containsKey("order_id");
        assertThat(rows.get(0)).containsKey("customer_id");
    }

    /**
     * Execute an aggregation transform: total revenue per customer.
     */
    @Test
    void aggregationTransform() throws Exception {
        String csvPath = getClass().getClassLoader().getResource("data/orders.csv").getPath();

        String transformSql = """
            SELECT
                customer_id,
                COUNT(*) AS order_count,
                SUM(quantity * price) AS total_revenue
            FROM input
            GROUP BY customer_id
            ORDER BY total_revenue DESC
            """;

        DuckDBService.TransformResult result = duckdb.executeTransform(csvPath, transformSql);

        assertThat(result.rowCount()).isEqualTo(5); // 5 unique customers
        assertThat(result.rows().get(0).get("customer_id")).isEqualTo("CUST001");
    }

    /**
     * Window function: rank customers by total spending.
     */
    @Test
    void windowFunctionTransform() throws Exception {
        String csvPath = getClass().getClassLoader().getResource("data/orders.csv").getPath();

        String transformSql = """
            SELECT
                customer_id,
                SUM(quantity * price) AS total_revenue,
                RANK() OVER (ORDER BY SUM(quantity * price) DESC) AS spending_rank
            FROM input
            GROUP BY customer_id
            """;

        DuckDBService.TransformResult result = duckdb.executeTransform(csvPath, transformSql);

        assertThat(result.rowCount()).isEqualTo(5);
        // Top spender gets rank 1
        var topSpender = result.rows().stream()
                .filter(r -> Long.valueOf(1L).equals(r.get("spending_rank")))
                .findFirst()
                .orElseThrow();
        assertThat(topSpender.get("customer_id")).isEqualTo("CUST001");
    }

    /**
     * Write transform output to Parquet, then read it back.
     */
    @Test
    void writeAndReadParquet() throws Exception {
        String csvPath = getClass().getClassLoader().getResource("data/orders.csv").getPath();
        Path outputPath = tempDir.resolve("customer_summary.parquet");

        String sql = "SELECT customer_id, SUM(quantity * price) AS total " +
                     "FROM read_csv('" + csvPath + "', header=true) " +
                     "GROUP BY customer_id";

        duckdb.writeToParquet(sql, outputPath.toString());

        assertThat(Files.exists(outputPath)).isTrue();

        List<Map<String, Object>> readBack = duckdb.readParquet(outputPath.toString());
        assertThat(readBack).hasSize(5);
    }

    /**
     * Profile a Parquet file: row count, column stats.
     */
    @Test
    void profileParquetFile() throws Exception {
        String csvPath = getClass().getClassLoader().getResource("data/orders.csv").getPath();
        Path parquetPath = tempDir.resolve("orders.parquet");

        duckdb.writeToParquet("SELECT * FROM read_csv('" + csvPath + "', header=true)", parquetPath.toString());

        DataProfiler.DataProfile profile = profiler.profile(parquetPath.toString());

        assertThat(profile.rowCount()).isEqualTo(10);
        assertThat(profile.columns()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(profile.columns().stream().map(DataProfiler.ColumnStats::name).toList())
                .contains("order_id", "customer_id", "product");
    }

    /**
     * Execute raw SQL query.
     */
    @Test
    void rawSqlQuery() throws Exception {
        List<Map<String, Object>> result = duckdb.query(
                "SELECT 1 + 1 AS sum, 'hello' AS greeting"
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("sum")).isEqualTo(2);
        assertThat(result.get(0).get("greeting")).isEqualTo("hello");
    }
}

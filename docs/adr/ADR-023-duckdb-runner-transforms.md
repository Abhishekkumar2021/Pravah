# ADR-023: DuckDB for In-Process Transforms on the Runner

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's runners execute ETL steps. The most common step type is a **transform**: read data from a source, apply SQL transformations, write results to a destination. This is the core "T" in ETL.

Options for executing transforms on the runner:

1. **External query engine**: connect to a remote database (the source itself), run the SQL there, then move data to the destination. Fast if the source supports the transformation, but locks the transform logic to the source's SQL dialect, requires the source to have compute capacity, and leaks data movement logic into the source system.

2. **Custom Java/Python code**: write transforms as Java or Python code inside the runner. Flexible but requires coding for every transform — not accessible to SQL-literate data analysts.

3. **Embedded SQL engine on the runner**: embed a SQL engine inside the runner process that can read data from sources into memory, execute standard SQL, and write results to destinations. Data engineers use familiar SQL; no external service dependency; no data leaves the runner's process boundary until the write step.

The runner needs an embedded SQL engine that:
- Runs in-process (no network dependency for the computation itself)
- Handles analytical SQL queries efficiently (aggregations, window functions, joins)
- Reads from multiple sources (JDBC, Parquet, CSV, S3)
- Writes to multiple destinations (JDBC, Parquet on MinIO, Snowflake via COPY)
- Processes datasets that exceed the runner's RAM (larger-than-memory query execution via external sort and spill-to-disk)
- Is embeddable in a Java process (JVM or native)

---

## Decision

**DuckDB** is the embedded in-process SQL engine for all transform steps on the Pravah runner.

**Why DuckDB:**

| Requirement | DuckDB Capability |
|-------------|-------------------|
| In-process, no network dependency | Single shared library, embedded in JVM via JDBC driver |
| Columnar, analytical SQL | Vectorized execution engine; 10–100× faster than row-based for analytics |
| Larger-than-memory queries | External aggregation and sort with automatic disk spill |
| Read from Parquet, CSV, S3 | Native `read_parquet()`, `read_csv()`, `httpfs` extension for S3 |
| Read from JDBC sources | `postgres_scan()`, `mysql_scan()` extensions |
| Write to Parquet (MinIO) | `COPY ... TO 's3://...' (FORMAT PARQUET)` |
| Java embedding | Official DuckDB JDBC driver |
| OpenLineage column tracking | SQL AST analysis extracts column-level lineage automatically |

**Runner transform execution flow:**

```
Runner receives JobAssignment:
  type: DUCKDB_TRANSFORM
  config:
    source_query: "SELECT * FROM orders WHERE created_at > '{{ last_run_at }}'"
    source_connection: "jdbc:postgresql://prod-db/..."
    transform_sql: |
      SELECT
        customer_id,
        SUM(revenue)      AS total_revenue,
        COUNT(*)          AS order_count,
        MAX(created_at)   AS last_order_date
      FROM input
      GROUP BY customer_id
    destination_type: PARQUET
    destination_path: "s3://pravah-artifacts/tenants/{tenant}/executions/{exec}/steps/{step}/output/data.parquet"

Runner execution:
  1. Download source data via JDBC → DuckDB in-memory table "input"
  2. Execute transform_sql against "input" → result table
  3. COPY result → Parquet on MinIO via httpfs extension
  4. Profile result: row count, column stats → profile.json
  5. Extract column lineage from SQL AST → OpenLineage event → Kafka
  6. Report success + output location to Execution Service
```

**DuckDB in the runner (Java):**

```java
public class DuckDBTransformExecutor {

    private Connection conn;

    public TransformResult execute(TransformConfig config, PreSignedUrls urls) throws Exception {
        conn = DriverManager.getConnection("jdbc:duckdb:");

        // Configure S3/MinIO access for this execution
        configureMinio(conn, urls.getMinioEndpoint(), urls.getPresignedUploadToken());

        // Step 1: Load source data into DuckDB
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("""
                CREATE TABLE input AS
                SELECT * FROM postgres_scan('%s', '%s', '%s')
                WHERE %s
            """, config.getJdbcUrl(), config.getSchema(), config.getTable(),
                 config.getWhereClause()));
        }

        // Step 2: Execute the transform
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("""
                CREATE TABLE output AS
                %s
            """, config.getTransformSql()));
        }

        // Step 3: Profile the output
        DataProfile profile = profiler.profile(conn, "output");

        // Step 4: Write output to MinIO as Parquet
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("""
                COPY output TO '%s' (FORMAT PARQUET, COMPRESSION ZSTD, ROW_GROUP_SIZE 100000)
            """, urls.getOutputParquetPath()));
        }

        // Step 5: Extract column lineage from the SQL AST
        ColumnLineage lineage = lineageExtractor.extract(config.getTransformSql(), "input", "output");

        return new TransformResult(profile, lineage, urls.getOutputParquetPath());
    }
}
```

**SQL lineage extraction from AST:**

DuckDB's AST can be queried via `duckdb_functions()`. For simple transforms, Pravah parses the SQL AST to automatically extract column-level lineage:

```java
// Parse the SQL to find column mappings
// SELECT customer_id, SUM(revenue) AS total_revenue FROM input
// → customer_id: input.customer_id (direct)
// → total_revenue: SUM(input.revenue) (aggregation)
ColumnLineage lineage = sqlAstParser.extractLineage(transformSql);
// → output in OpenLineage columnLineage facet format
```

**Memory management:**

DuckDB's memory limit is configured per runner based on the job's resource request:

```java
stmt.execute("SET memory_limit = '4GB'");   // from job resource spec
stmt.execute("SET temp_directory = '/tmp/duckdb-spill'");  // spill to disk if exceeded
```

A 4GB memory limit means DuckDB can process a 40GB dataset by spilling intermediate results to disk (external sort, out-of-core aggregation).

**Security: SQL sandbox:**

Transform SQL is user-defined code that runs on the runner. The runner enforces:
- DuckDB runs as a non-root process
- Only `SELECT`, `CREATE TABLE AS`, and `COPY` are permitted in transform SQL (validated before execution)
- `httpfs` extension can only write to the pre-signed URL provided by the Execution Service (path validated)
- No `shell()`, `read_csv('/etc/passwd')`, or other filesystem access outside designated paths

---

## Consequences

### Positive

- **No external service dependency for computation**: transforms run entirely within the runner process. A 10GB dataset transformation does not require a Spark cluster or a remote database. The runner is self-sufficient.
- **Columnar execution is significantly faster for analytics**: DuckDB's vectorized execution processes analytical queries (aggregations, window functions, joins) 10–100× faster than row-based JDBC iteration. A transformation that takes 5 minutes in a Java streaming loop takes 20 seconds in DuckDB.
- **Standard SQL**: data engineers write familiar SQL. No proprietary DSL, no custom operator framework. Any SQL-literate person can write Pravah transform steps.
- **Automatic column-level lineage**: DuckDB's SQL parser provides an AST that Pravah can analyze to automatically emit OpenLineage `columnLineage` facets. Manual annotation is not required.
- **Larger-than-memory processing**: DuckDB's external sort and out-of-core aggregation handle datasets that exceed the runner's RAM by spilling to disk. This eliminates the OOM failures common with in-memory processing.
- **Parquet as the interchange format**: output as Parquet means the next step can read directly from MinIO using `read_parquet()` — no intermediate database write required. Parquet's columnar format and compression (ZSTD) reduce storage size by 5–10× compared to CSV.

### Negative

- **DuckDB is single-machine**: DuckDB does not distribute processing across multiple nodes. A single runner handles the entire transform. For very large datasets (TB+), the runner must have enough CPU and memory (or disk for spill). Distributed transforms require Spark or Flink.
- **JVM embedding overhead**: DuckDB's native library is loaded via JNI in the Java runner. Library loading adds ~500ms to runner startup. This is a one-time cost per runner process, not per job.
- **SQL validation surface**: user-provided SQL must be sandboxed carefully. A malicious `COPY ... TO '/etc/cron.d/...'` could write to unexpected locations. Strict SQL parsing and allowlist validation is required.
- **DuckDB JDBC driver maturity**: DuckDB's Java integration is newer than its Python integration. Some edge cases in the JDBC driver require workarounds.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Malicious SQL accesses filesystem | Transform SQL validated with whitelist parser before execution; only SELECT/CREATE/COPY allowed |
| DuckDB OOM on unexpectedly large dataset | Memory limit enforced; spill to disk configured; runner reports OOM as TRANSIENT_ERROR for retry with larger resource allocation |
| DuckDB version mismatch between runner binary and cloud expectations | DuckDB version pinned in runner build; version reported in runner registration; cloud validates compatibility |
| Spill directory fills up disk | Temp directory on dedicated volume with size limit; DuckDB fails gracefully when temp space exhausted |

---

## Alternatives Considered

### Apache Spark on the Runner

Embed Spark in the runner for distributed processing.

Rejected because:
- Spark requires a JVM driver + executor model, which is incompatible with the "single runner process" model
- Spark's startup time (15–30 seconds per job) is prohibitive for short-duration steps
- Spark's memory overhead (~500MB minimum) is too high for lightweight transforms
- DuckDB handles analytical workloads up to several hundred GB efficiently without the operational complexity of Spark

### Custom Java Streaming Pipeline

Write data transformation logic as Java code (read rows from source, transform, write to destination).

Rejected because:
- Row-by-row Java streaming is 10–100× slower than columnar DuckDB for analytical transforms
- Every transform requires custom Java code — not accessible to SQL-literate data analysts
- No automatic column lineage extraction possible from arbitrary Java code

### Push Transform to Source Database

Run `INSERT INTO destination SELECT ... FROM source` directly on the source database.

Rejected because:
- Not all source databases support writing to the destination (e.g., PostgreSQL cannot write to Snowflake natively)
- Puts compute load on the source database — potentially impacting production queries
- Requires source database credentials on the destination side
- Lineage extraction is source-specific — no standardized AST across all source types

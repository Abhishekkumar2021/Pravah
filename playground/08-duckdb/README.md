# Playground 08 — DuckDB

**ADR**: [ADR-023 DuckDB for Runner Transforms](../../docs/adr/ADR-023-duckdb-runner-transforms.md)  
**Concepts**: In-process SQL engine, columnar execution, Parquet I/O, larger-than-memory processing

---

## Why DuckDB?

Pravah runners execute ETL **transforms**: read data, apply SQL, write results. Options:

| Approach | Problem |
|----------|---------|
| External query engine (push to source DB) | Locks to source's SQL dialect, adds load to source |
| Custom Java code | Requires coding for every transform, not SQL-friendly |
| **Embedded SQL engine (DuckDB)** | SQL-native, no network dependency, columnar speed |

DuckDB is an **in-process analytical database** — like SQLite, but optimized for analytics (aggregations, window functions, joins). It runs inside the JVM via JDBC, reads Parquet/CSV/JSON directly, and handles larger-than-memory datasets by spilling to disk.

---

## Quick Start

```bash
cd /Users/abhishek/Dev/Pravah/playground/08-duckdb

# Run tests (no Docker needed — DuckDB is embedded)
./gradlew test
```

---

## What's in Here

| File | Purpose |
|------|---------|
| `DuckDBService.java` | Core service: load data, execute transforms, write Parquet |
| `DataProfiler.java` | Generate column stats using DuckDB's SUMMARIZE |
| `data/orders.csv` | Sample dataset (10 orders) |
| `DuckDBIT.java` | Tests: read CSV, aggregations, window functions, Parquet I/O |

---

## Key Capabilities Demonstrated

### 1. Read from CSV/Parquet directly

```java
// No ETL needed — DuckDB reads files directly
duckdb.query("SELECT * FROM read_csv('orders.csv', header=true)");
duckdb.query("SELECT * FROM read_parquet('data.parquet')");
```

### 2. Analytical SQL (aggregations, window functions)

```sql
-- Aggregation: total revenue per customer
SELECT customer_id, SUM(quantity * price) AS total_revenue
FROM input
GROUP BY customer_id

-- Window function: rank by spending
SELECT customer_id, SUM(quantity * price) AS total,
       RANK() OVER (ORDER BY SUM(quantity * price) DESC) AS rank
FROM input
GROUP BY customer_id
```

### 3. Write to Parquet

```java
duckdb.writeToParquet(
    "SELECT customer_id, SUM(quantity * price) AS total FROM input GROUP BY 1",
    "/output/customer_summary.parquet"
);
```

### 4. Data profiling

```java
DataProfile profile = profiler.profile("output.parquet");
// → row count, column types, min/max, null %, approx distinct
```

---

## Tasks

### 1. Explore the transform flow

Read `DuckDBService.executeTransform()`. Notice the pattern:
1. Load source into `input` table
2. Execute transform SQL → `output` table
3. Extract results

This is exactly what Pravah runners do for `DUCKDB_TRANSFORM` jobs.

### 2. Try a JOIN transform

Add a `customers.csv` with customer metadata. Write a transform that joins orders with customers:

```sql
SELECT o.*, c.name, c.region
FROM input o
JOIN read_csv('customers.csv') c ON o.customer_id = c.customer_id
```

### 3. Test larger-than-memory

Generate a large CSV (1M+ rows) and run an aggregation. Watch DuckDB spill to disk:

```sql
SET temp_directory = '/tmp/duckdb-spill';
SET memory_limit = '128MB';
SELECT customer_id, SUM(total) FROM large_data GROUP BY 1;
```

### 4. Explore SUMMARIZE

Run `SUMMARIZE SELECT * FROM read_parquet('...')` interactively. See how Pravah uses this for automatic data quality checks.

### 5. Column lineage (advanced)

DuckDB exposes SQL AST. Explore `duckdb_functions()` and `duckdb_keywords()` to understand how Pravah extracts column-level lineage from transform SQL.

---

## Why Not Spark?

| DuckDB | Spark |
|--------|-------|
| Single-process, embedded | Distributed, requires cluster |
| ~500ms startup | 15-30s startup |
| Handles GBs efficiently | Designed for TBs across nodes |
| No infrastructure | Requires driver + executors |

For Pravah's use case (transforms up to hundreds of GBs on a single runner), DuckDB is faster to start, simpler to operate, and sufficient for the workload.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `ClassNotFoundException: DuckDB` | Check `duckdb_jdbc` dependency in build.gradle.kts |
| OOM on large query | Increase `memory_limit` or ensure `temp_directory` has space |
| "File not found" on CSV | Use absolute path or classpath resource |

---

## Further Reading

- [DuckDB Documentation](https://duckdb.org/docs/)
- [DuckDB JDBC Driver](https://duckdb.org/docs/api/java)
- ADR-023 in this repo

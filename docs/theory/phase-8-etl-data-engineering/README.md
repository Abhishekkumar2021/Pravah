# Phase 8 — ETL & Data Engineering

This phase covers the core data engineering concepts that underpin Pravah's pipeline execution. These are not generic computer science fundamentals — they are the specific engineering disciplines required to move, transform, and validate data at production scale in a multi-tenant orchestration platform.

Each chapter connects directly to a Pravah design decision: CDC Internals explains why the Connect Service uses Debezium; Data Contracts explains the schema registry and drift detection; Backfill Safety explains ADR-025; Data Lineage explains ADR-019; DuckDB explains ADR-023; Data Quality explains the quality scoring in the Metadata Service.

---

## Chapters

| # | Chapter | Key Topics |
|---|---------|-----------|
| 8.1 | [DAG Engine Design — From Theory to Implementation](8.1-dag-engine-design.md) | Topological sort, dependency resolution, fan-out/fan-in, concurrency |
| 8.2 | [CDC Internals & Debezium](8.2-cdc-internals-debezium.md) | WAL tailing, snapshot mode, offset management, exactly-once |
| 8.3 | [Data Contracts & Schema Evolution](8.3-data-contracts-schema-evolution.md) | Contracts, compatibility rules, Schema Registry, drift detection |
| 8.4 | [Backfill Safety & Idempotency in Practice](8.4-backfill-safety-idempotency.md) | Safe re-runs, MERGE semantics, window functions, partition overwrite |
| 8.5 | [Data Lineage — OpenLineage in Practice](8.5-data-lineage-openlineage.md) | Column-level lineage, impact analysis, Marquez, Elasticsearch graph |
| 8.6 | [DuckDB & Columnar Processing](8.6-duckdb-columnar-processing.md) | Vectorized execution, Parquet, Arrow, larger-than-memory, DuckDB SQL |
| 8.7 | [Data Quality Frameworks](8.7-data-quality-frameworks.md) | Profiling, expectations, Great Expectations, dbt tests, quality scores |

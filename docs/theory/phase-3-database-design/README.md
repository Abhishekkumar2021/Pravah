# Phase 3 — Database Design & Scaling

> **Status:** ✅ Complete — 8 of 8 chapters

Pravah's database layer is PostgreSQL-first, with Redis for state and caching, Elasticsearch for search and lineage, and MinIO for object storage. This phase covers every decision from schema isolation to partitioning billions of rows.

By the end of Phase 3 you will understand:
- How PostgreSQL works internally (MVCC, WAL, VACUUM) and why it matters for Pravah's write-heavy workload
- How to design indexes that are actually used at scale — not just correct in development
- How to partition, pool connections, replicate, and eventually shard without downtime
- Where Redis fits alongside PostgreSQL and what it handles better

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [3.1](3.1-database-per-service.md) | **Database Per Service** | Why shared databases fail; Pravah's isolation strategy; cross-service data access patterns |
| [3.2](3.2-postgresql-internals-mvcc-wal-vacuum.md) | **PostgreSQL Internals: MVCC, WAL & Vacuum** | How PG achieves concurrency without locks; WAL durability; vacuum and table bloat |
| [3.3](3.3-indexing-strategies.md) | **Indexing Strategies** | B-tree, GIN, BRIN, partial, covering indexes — with the exact indexes Pravah uses on every major table |
| [3.4](3.4-table-partitioning.md) | **Table Partitioning** | Monthly range partitioning of `job_runs`; partition pruning; `pg_partman`; instant DROP for retention |
| [3.5](3.5-connection-pooling-pgbouncer.md) | **Connection Pooling & PgBouncer** | Transaction-mode pooling; connection exhaustion math; gotchas with prepared statements and advisory locks |
| [3.6](3.6-read-replicas-replication-lag.md) | **Read Replicas & Replication Lag** | Streaming replication internals; read routing with `AbstractRoutingDataSource`; lag handling; failover behavior |
| [3.7](3.7-sharding-strategies.md) | **Sharding Strategies** | The scaling ladder before sharding; `tenant_id` as shard key; Citus distributed PostgreSQL; consistent hashing; rebalancing |
| [3.8](3.8-redis-caching-locking-pubsub.md) | **Redis — Caching, Locking & Pub/Sub** | Data structures; cache-aside vs write-through vs write-behind; distributed locks with fencing tokens; pub/sub for WebSocket fanout |

---

## Pravah's Database Architecture

```
Service                  Database               Notes
──────────────────────────────────────────────────────────────────────────
Pipeline Service         PostgreSQL             Pipeline definitions, versions, DAG
Scheduler Service        PostgreSQL             Job queue, schedule config, backfill state
Execution Service        PostgreSQL             Job run history, saga state, assignment log
Runner Service           PostgreSQL + Redis      Registry in PG; live heartbeat state in Redis
Agent Service            PostgreSQL             Healing actions, drift events
Metadata Service         PostgreSQL + ES         Schema catalog in PG; lineage in ES
Billing Service          PostgreSQL (partitioned) Usage events, invoices, quotas
Audit Service            PostgreSQL (append-only) Immutable audit trail, 365-day retention

Shared caching           Redis                  Pipeline config cache, runner heartbeats
Leader election          Redis (Redlock)         Scheduler leader, partition coordinator
Rate limiting            Redis                  Per-tenant API rate limit counters
Real-time signals        Redis pub/sub           WebSocket job event fanout
Artifacts                MinIO                  Pipeline outputs, log bundles, snapshots
Log search               Elasticsearch          Aggregated logs, audit search
Lineage                  Elasticsearch          Column-level lineage graph, data catalog
```

> Each service owns its own database. No service reads another service's database directly. Cross-service data access happens via API calls or Kafka events.

---

## The `job_runs` Table — Design Journey Through Phase 3

```
Chapter 3.1  Database Per Service
             Isolated in execution_db — no shared schema with other services

Chapter 3.2  PostgreSQL Internals
             MVCC: 10K inserts/min + dashboard reads = zero lock contention
             WAL: Debezium CDC reads WAL → Kafka → audit/billing
             Vacuum tuning: autovacuum_vacuum_scale_factor = 0.01 on hot table

Chapter 3.3  Indexing
             Composite: (tenant_id, status, created_at DESC)
             Partial: WHERE status IN ('PENDING','RUNNING','FAILED') — 2% of rows
             Covering: INCLUDE (pipeline_id, runner_id, duration_ms) — zero heap fetches

Chapter 3.4  Table Partitioning
             Range-partitioned by created_at — monthly buckets
             50M rows/month → 15 GB/partition
             DROP TABLE partition_old → milliseconds, zero WAL

Chapter 3.5  Connection Pooling
             PgBouncer transaction mode: 150 app threads → 25 PG connections
             Disables server-side prepared statements
             SET defaults at ALTER DATABASE level

Chapter 3.6  Read Replicas
             Dashboard reads → async replica (lag acceptable)
             Write + after-write reads → primary
             AbstractRoutingDataSource + @ReadOnly annotation

Chapter 3.7  Sharding (future)
             Citus by tenant_id when write throughput becomes the bottleneck
             Not needed at current scale — partitioning + replicas sufficient

Chapter 3.8  Redis
             Heartbeats: SET runner:heartbeat:001 EX 30
             Lock: SET scheduler:leader pod-1 NX PX 30000 (Redlock for safety)
             Cache: pipeline:config:pipe-abc TTL 5m, invalidate on write
```

---

## Key Numbers to Know

```
Metric                                Value            Notes
────────────────────────────────────────────────────────────────────────
job_runs rows per month               ~50 million      Pravah at scale
job_runs partition size               ~15 GB           Monthly bucket
Index size without partitioning       ~10 GB           B-tree over 8B rows
Index size per partition              ~500 MB          B-tree over 50M rows
VACUUM full table (no partitioning)   Hours            Blocks all queries
DROP TABLE partition                  Milliseconds     Just catalog entry
PostgreSQL max_connections            ~500             Practical RAM limit
PgBouncer pool size (execution_db)    25               Serves 150 app threads
Replication lag (async replica)       1–100ms          Acceptable for dashboards
Redis GET latency                     100–500µs        vs 1–10ms PostgreSQL
Redis SET NX PX (lock acquire)        <1ms             Atomic, no locks
```

---

## Navigation

← [Phase 2 — Messaging & Kafka Internals](../phase-2-kafka-messaging/README.md)
→ [Phase 4 — Observability & Reliability](../phase-4-observability/README.md) *(next)*

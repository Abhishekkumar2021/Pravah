# Phase 3 — Database Design & Scaling

> **Status:** 🔄 In Progress — 4 of 12 chapters complete

Pravah's database layer is PostgreSQL-first, with Redis for state and caching, Elasticsearch for search and lineage, and MinIO for object storage. This phase covers every decision from schema isolation to partitioning billions of rows.

By the end of Phase 3 you will understand:
- How PostgreSQL works internally (MVCC, WAL, VACUUM) and why it matters for Pravah's write-heavy workload
- How to design indexes that are actually used at scale — not just correct in development
- How to partition, pool connections, and replicate without downtime

---

## Chapters

| # | Chapter | What You'll Learn | Status |
|---|---------|-------------------|--------|
| [3.1](3.1-database-per-service.md) | **Database Per Service** | Why shared databases fail; Pravah's isolation strategy; cross-service data access patterns | ✅ |
| [3.2](3.2-postgresql-internals-mvcc-wal-vacuum.md) | **PostgreSQL Internals: MVCC, WAL & Vacuum** | How PG achieves concurrency without locks; WAL durability; vacuum and table bloat | ✅ |
| [3.3](3.3-indexing-strategies.md) | **Indexing Strategies** | B-tree, GIN, BRIN, partial, covering indexes — with the exact indexes Pravah uses on every major table | ✅ |
| [3.4](3.4-table-partitioning.md) | **Table Partitioning** | Monthly range partitioning of `job_runs`; partition pruning; `pg_partman`; instant DROP for retention | ✅ |
| 3.5 | **Connection Pooling & PgBouncer** | Transaction-mode pooling; connection exhaustion; gotchas with prepared statements and advisory locks | 🔜 |
| 3.6 | **Read Replicas & Replication Lag** | Streaming replication; lag monitoring; read routing; promotion on primary failure | 📋 |
| 3.7 | **Sharding Strategies** | When Postgres isn't enough; horizontal sharding with Citus; application-level shard routing | 📋 |
| 3.8 | **Redis — Caching, Locking & Pub/Sub** | Cache-aside vs write-through; distributed locks with Redisson; pub/sub for runner notifications | 📋 |
| 3.9 | **Cache Invalidation Strategies** | TTL, event-driven invalidation, cache stampede prevention | 📋 |
| 3.10 | **Cold Storage Archival** | When to archive vs delete; S3/MinIO cold tiers; query federation | 📋 |
| 3.11 | **Elasticsearch for Lineage & Log Search** | Index design for log search; lineage graph queries; relevance scoring | 📋 |
| 3.12 | **NewSQL vs NoSQL** | When to deviate from Postgres; CockroachDB, Cassandra, DynamoDB trade-offs | 📋 |

---

## Pravah's Database Architecture

```
Service                Database             Notes
──────────────────────────────────────────────────────────────────────────────
Pipeline Service        PostgreSQL           Pipeline definitions, versions, DAG
Scheduler Service       PostgreSQL           Job queue, schedule config, backfill state
Execution Service       PostgreSQL           Job run history, assignment log
Runner Service          PostgreSQL + Redis   Registry in PG; live state in Redis
Agent Service           PostgreSQL           Healing actions, drift events
Metadata Service        PostgreSQL + ES      Schema catalog in PG; lineage in ES
Notification Service    PostgreSQL           Alert rules, delivery receipts
Vault Service           HashiCorp Vault      Secrets — not PostgreSQL

Shared caching          Redis                Job status cache, runner heartbeats
Artifacts               MinIO                Pipeline outputs, log bundles
Log search              Elasticsearch        Aggregated logs, audit search
```

> Each service owns its own database. No service reads another service's database directly. Cross-service data access happens via API calls or Kafka events.

---

## The `job_runs` Table — Design Evolution

```
job_runs journey through Phase 3
──────────────────────────────────────────────────────────
Chapter 3.1  Isolated per service; owns its own schema
Chapter 3.2  MVCC means reads never block writes (no locks for SELECT)
Chapter 3.3  Composite index (tenant_id, status, created_at DESC)
             Partial index WHERE status IN ('PENDING','RUNNING','FAILED')
             Covering index to avoid heap fetches on hot query
Chapter 3.4  Range-partitioned by created_at, monthly buckets
             50M rows/month → 15 GB/partition
             DROP TABLE partition_old → instant, zero WAL
Chapter 3.5  PgBouncer in front → 10 app instances × 20 conns
             = 200 PG connections (vs 2000 without pooling)
```

---

## Key Numbers to Know

```
Metric                          Value           Source
────────────────────────────────────────────────────────
job_runs rows per month         ~50 million     Pravah at scale
job_runs partition size         ~15 GB          Monthly bucket
Index size (no partitioning)    ~10 GB          B-tree over 8B rows
Index size (per partition)      ~500 MB         B-tree over 50M rows
VACUUM full table (no part.)    hours           Blocking on large tables
DROP TABLE partition            milliseconds    Just catalog entry deletion
PostgreSQL max connections       ~500            Practical limit (RAM)
PgBouncer transaction-mode      10,000+         Multiplexed connections
```

---

## Navigation

← [Phase 2 — Messaging & Kafka Internals](../phase-2-kafka-messaging/README.md)  
→ Phase 4 — Observability & Reliability *(coming soon)*

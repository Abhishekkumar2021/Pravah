# ADR-029: Connect Service & CDC Pipeline Management

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's data extraction layer includes two distinct modes that go beyond simple JDBC batch extraction:

**Change Data Capture (CDC)** — continuously stream database changes (inserts, updates, deletes) from source databases to Kafka in near-real-time, without polling the source tables. This is the foundation for streaming pipelines and eliminates the `updated_at` column dependency that cursor-based incremental sync requires.

**Sink Connectors** — write from Kafka topics to destinations (Elasticsearch, S3, BigQuery, Snowflake) continuously, without a Pravah pipeline running a full extract-load cycle.

Both are implemented via **Apache Kafka Connect** with **Debezium** (CDC) and various sink connector plugins. Kafka Connect is a powerful framework but is operationally complex: connector lifecycle, configuration management, lag monitoring, restart policies, and error handling all require careful management.

The question is: does Pravah expose raw Kafka Connect APIs to users, or does it wrap them in a managed abstraction?

---

## Decision

A dedicated **Connect Service** wraps the Kafka Connect REST API, providing a managed interface for CDC and sink connector lifecycle. Users never interact with raw Kafka Connect; they configure connectors through Pravah's unified API.

**What the Connect Service provides:**

```
User (Pravah UI / API)
    │
    │  POST /v1/connectors
    │  {type: "postgres-cdc", source: {...}, target_topic: "orders.cdc"}
    ▼
Connect Service
    ├── Validates configuration
    ├── Resolves credentials from Vault (never stores raw credentials)
    ├── Translates to Kafka Connect connector config
    ├── Calls Kafka Connect REST API: PUT /connectors/{name}/config
    ├── Monitors connector status and lag
    ├── Publishes connector health events to Kafka
    └── Handles restart, pause, resume lifecycle
    │
    ▼
Kafka Connect Cluster
    ├── Debezium PostgreSQL Source Connector (CDC)
    ├── Debezium MySQL Source Connector (CDC)
    ├── S3 Sink Connector
    ├── Elasticsearch Sink Connector
    └── Snowflake Sink Connector
```

**CDC connector configuration (user-facing Pravah API):**

```json
{
  "name": "orders-cdc",
  "type": "CDC_SOURCE",
  "source": {
    "database_type": "POSTGRESQL",
    "connection_secret": "vault:secret/megacorp/postgres-prod",
    "database": "orders_db",
    "tables": ["public.orders", "public.order_items"],
    "slot_name": "pravah_megacorp_orders",
    "publication_name": "pravah_pub"
  },
  "output": {
    "topic_prefix": "megacorp.cdc",
    "include_schema": true
  },
  "options": {
    "snapshot_mode": "initial",
    "decimal_handling": "string",
    "tombstone_on_delete": true
  }
}
```

The Connect Service translates this into the full Debezium connector JSON (with Vault credential resolution) before calling Kafka Connect.

**Connector lifecycle management:**

| State | Description | Automatic Action |
|-------|-------------|-----------------|
| RUNNING | Consuming CDC events normally | Monitor lag |
| PAUSED | Suspended by user or system | Resume when trigger condition met |
| FAILED | Connector threw an exception | Auto-restart up to 3 times with backoff |
| RESTARTING | Recovery in progress | Wait, then check |
| DESTROYED | Explicitly deleted | Clean up replication slot |

**Critical: Replication slot management:**

Debezium uses PostgreSQL replication slots to track WAL position. An abandoned replication slot causes PostgreSQL WAL to accumulate indefinitely — a disk-filling disaster. The Connect Service manages this risk:

- When a connector is DESTROYED: Connect Service explicitly calls `SELECT pg_drop_replication_slot(slot_name)` via the Pipeline Service's database admin API
- When a connector has been FAILED for > 24 hours: alert sent, slot monitoring kicks in
- Replication slot lag is monitored: `SELECT slot_name, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes FROM pg_replication_slots`
- Alert if lag_bytes > 10GB (source DB disk risk)

**Lag monitoring:**

The Connect Service publishes connector health metrics to Prometheus:

```
pravah_connector_lag_bytes{connector="orders-cdc", tenant="megacorp"}
pravah_connector_messages_per_second{connector="orders-cdc"}
pravah_connector_status{connector="orders-cdc"} # 1=RUNNING, 0=other
```

Grafana alerts on connector lag > 1GB (connector falling behind) or connector status != RUNNING for > 5 minutes.

**Sink connectors:**

CDC sources produce to Kafka topics. Sink connectors continuously consume from those topics and write to destinations:

```json
{
  "name": "orders-to-snowflake",
  "type": "SINK",
  "source_topic": "megacorp.cdc.public.orders",
  "destination": {
    "type": "SNOWFLAKE",
    "connection_secret": "vault:secret/megacorp/snowflake-dw",
    "database": "DW",
    "schema": "STAGING",
    "table": "ORDERS"
  },
  "options": {
    "upsert_mode": true,
    "primary_key": "order_id",
    "batch_size": 1000
  }
}
```

---

## Consequences

### Positive

- Users get a clean, credential-safe abstraction over Kafka Connect. They never see raw connector JSON or manage Vault secret paths manually.
- Replication slot lifecycle is managed automatically — the most dangerous operational risk of raw Debezium usage is eliminated.
- Connector health metrics are first-class Prometheus metrics visible in the same Grafana dashboards as pipeline metrics.
- The Connect Service can be evolved (e.g., migrating from Kafka Connect to Flink CDC) without changing the user-facing API.

### Negative

- The Connect Service is a pass-through for most operations. It adds a layer of indirection that can complicate debugging ("the connector is configured right in Pravah, why is it wrong in Kafka Connect?").
- Supporting every Kafka Connect connector plugin requires the Connect Service to know the configuration schema for each. A new plugin requires updating the Connect Service.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Kafka Connect cluster unavailable | Connect Service queues configuration changes and applies them when cluster recovers; existing running connectors continue independently |
| Replication slot abandoned on Connect Service crash | Connect Service stores slot names in its own database; on startup, reconciles running connectors with known slots and alerts on orphans |
| Schema changes break sink connector | Schema Registry enforces backward compatibility; Connect Service validates schema compatibility before applying CDC connector changes |

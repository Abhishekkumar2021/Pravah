# ADR-019: OpenLineage Standard & Metadata Service

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah moves data. Understanding where data comes from, where it goes, and what transforms it in between — **data lineage** — is one of the core differentiators of the platform. Without lineage:

- A data analyst cannot answer: "Which pipelines feed this dashboard's dataset?"
- A data engineer cannot answer: "If I change this source table's schema, which pipelines break?"
- An incident responder cannot answer: "This column has bad values — which pipeline step introduced them?"
- A compliance officer cannot answer: "Show me every system that processes our customers' email addresses"

Lineage must be captured at the **column level**, not just at the dataset or table level. "Pipeline X reads from Table A and writes to Table B" is insufficient. "Pipeline X reads `orders.customer_email`, masks it, and writes `dw.orders.email_hash`" is actionable lineage.

The question is: what standard format to use, and what system to store and serve it from.

**Standards landscape:**

- **OpenLineage** (CNCF, Linux Foundation) — open specification for lineage events. Producers emit structured `RunEvent` JSON. Consumers store and serve it. Supported by Airflow, dbt, Spark, Flink. Vendor-neutral.
- **Atlas** (Apache) — full data governance platform. Heavily coupled to the Hadoop ecosystem. Operational overhead is very high.
- **Proprietary** — build a custom lineage model. Maximum flexibility, zero ecosystem compatibility.

---

## Decision

**OpenLineage** is the standard for all lineage events in Pravah. A dedicated **Metadata Service** owns lineage storage (Elasticsearch), the data catalog, and schema registry integration.

**OpenLineage event structure:**

```json
{
  "eventType": "COMPLETE",
  "eventTime": "2026-05-10T09:00:05.000Z",
  "run": {
    "runId": "job-uuid-abc123",
    "facets": {
      "parent": {
        "run":  { "runId": "execution-uuid-xyz" },
        "job":  { "namespace": "pravah", "name": "pipeline-orders-v3" }
      }
    }
  },
  "job": {
    "namespace": "pravah",
    "name": "pipeline-orders-v3.transform-step",
    "facets": {
      "sql": { "query": "SELECT customer_id, SHA256(email) as email_hash FROM orders" }
    }
  },
  "inputs": [{
    "namespace": "postgres://prod-db",
    "name": "public.orders",
    "facets": {
      "schema": {
        "fields": [
          { "name": "customer_id", "type": "BIGINT" },
          { "name": "email",       "type": "VARCHAR" }
        ]
      }
    }
  }],
  "outputs": [{
    "namespace": "snowflake://dw.company.com",
    "name": "analytics.dw_orders",
    "facets": {
      "schema": {
        "fields": [
          { "name": "customer_id", "type": "BIGINT" },
          { "name": "email_hash",  "type": "VARCHAR" }
        ]
      },
      "columnLineage": {
        "fields": {
          "customer_id": { "inputFields": [{ "namespace": "postgres://prod-db", "name": "public.orders", "field": "customer_id" }] },
          "email_hash":  { "inputFields": [{ "namespace": "postgres://prod-db", "name": "public.orders", "field": "email",       "transformationDescription": "SHA256 hash" }] }
        }
      }
    }
  }]
}
```

This single event records that `orders.email` was SHA256-hashed to produce `dw_orders.email_hash` — column-level lineage with transformation description.

**Who emits lineage events:**

| Emitter | When | Via |
|---------|------|-----|
| Runner (DuckDB transform) | After each step completes | Kafka topic: `lineage.events` |
| Connect Service (Debezium) | On CDC pipeline completion | Kafka topic: `lineage.events` |
| Flink jobs | Via OpenLineage Flink integration | Kafka topic: `lineage.events` |
| dbt runner | Via OpenLineage dbt integration | Kafka topic: `lineage.events` |

All lineage events flow through Kafka. The Metadata Service is the sole consumer and storage owner.

**Metadata Service architecture:**

```
Kafka: lineage.events
    │
    ▼
Metadata Service (OpenLineage consumer)
    │
    ├── Store raw event → Elasticsearch (lineage-events-* index)
    ├── Update dataset catalog → PostgreSQL (datasets, fields, owners)
    ├── Update column lineage graph → Elasticsearch (lineage-graph index)
    └── Detect schema drift → publish schema.drift.detected to Kafka

Metadata Service (REST API)
    ├── GET /v1/lineage/{dataset_id}        → column-level lineage graph
    ├── GET /v1/impact/{dataset_id}         → what breaks if this changes
    ├── GET /v1/catalog/datasets            → search + browse
    ├── GET /v1/catalog/datasets/{id}/runs  → pipeline runs for this dataset
    └── GET /v1/schema/{namespace}/{name}   → current schema + history
```

**Elasticsearch index design for lineage graph:**

```json
// lineage-graph index — one document per (job, dataset) edge
{
  "run_id":           "job-uuid-abc",
  "job_namespace":    "pravah",
  "job_name":         "pipeline-orders-v3.transform-step",
  "dataset_namespace":"postgres://prod-db",
  "dataset_name":     "public.orders",
  "direction":        "INPUT",
  "column_lineage": [
    { "source_field": "email", "target_field": "email_hash",
      "transform": "SHA256", "target_dataset": "snowflake://dw/analytics.dw_orders" }
  ],
  "occurred_at":      "2026-05-10T09:00:05.000Z",
  "tenant_id":        "megacorp"
}
```

Graph traversal (impact analysis — what breaks if `orders.email` changes):

```
Elasticsearch query:
  direction=INPUT AND dataset_name=public.orders AND column_lineage.source_field=email
  → find all jobs that read this column
  → for each job, find all OUTPUT datasets
  → recurse for each output dataset
  → build the downstream impact graph
```

**Schema drift detection:**

When a new `RunEvent` arrives with an input dataset schema, the Metadata Service compares it to the last-known schema for that dataset:

```
New event: orders schema has 8 columns
Last known: orders schema had 7 columns
→ New column detected: "loyalty_tier"
→ Publish schema.drift.detected event
→ Agent Service receives event → analyzes impact → notifies pipeline owners
```

---

## Consequences

### Positive

- **Ecosystem compatibility**: OpenLineage is emitted by Airflow, dbt, Spark, Flink, and Trino out of the box. If a customer runs these tools alongside Pravah, lineage flows into the same catalog automatically.
- **Column-level lineage is standard**: the `columnLineage` facet in the OpenLineage spec provides exactly the column-to-column mapping Pravah needs. No custom schema design required.
- **Schema drift detection is automatic**: comparing incoming OpenLineage events against stored schemas catches drift without polling source systems.
- **Elasticsearch graph traversal at scale**: impact analysis (what downstream pipelines break if this column changes) is a graph traversal that Elasticsearch handles efficiently via nested queries and scrolling.
- **Decoupled from execution**: lineage events are published to Kafka asynchronously. Runner execution is not blocked by lineage storage. A Metadata Service outage does not affect pipeline execution.

### Negative

- **Elasticsearch operational overhead**: Elasticsearch is a complex system to operate at production scale (cluster sizing, shard management, mapping updates, index lifecycle). It joins PostgreSQL, Redis, Kafka, and Vault as production dependencies.
- **OpenLineage event generation is runner's responsibility**: the runner must emit correctly structured OpenLineage events for every step. This is non-trivial for custom connectors.
- **Graph traversal performance degrades with deep lineage**: impact analysis that traverses 10 levels of lineage edges requires careful Elasticsearch query design to avoid timeout.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Runner emits malformed OpenLineage event | Metadata Service validates against OpenLineage JSON schema; malformed events → DLQ |
| Elasticsearch index grows unboundedly | ILM policy: hot (30 days), warm (90 days), archive to MinIO (>90 days) |
| Column lineage graph traversal times out | Max traversal depth = 10 levels; result cached in Redis for 5 minutes |
| Schema drift false positive (transient column rename) | Drift detection requires 2 consecutive events with different schema before alerting |

---

## Alternatives Considered

### Apache Atlas

Full data governance platform with rich lineage support.

Rejected because:
- Atlas is heavily coupled to the Hadoop/Hive ecosystem. Running it in a Kubernetes-native environment is complex.
- Atlas's operational footprint (HBase or Cassandra backend, Kafka, Solr) is significantly larger than Elasticsearch alone.
- OpenLineage is a lighter, vendor-neutral specification that achieves the same lineage goals without the governance platform overhead.

### Custom Lineage Schema

Design a custom PostgreSQL schema for lineage with a graph representation (adjacency list, closure table).

Rejected because:
- Graph queries (impact analysis, traversal) are not PostgreSQL's strength. Multi-hop graph traversal in PostgreSQL requires recursive CTEs that are slow for deep lineage.
- OpenLineage provides a battle-tested, extensible schema with community tooling (Marquez UI for lineage visualization).
- Building a custom schema means building custom producer SDKs for each tool (DuckDB runner, Flink, dbt). OpenLineage provides these as standard library integrations.

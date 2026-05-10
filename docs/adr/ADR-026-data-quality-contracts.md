# ADR-026: Data Quality & Data Contracts

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah moves data between systems. The value of the platform depends entirely on the data that arrives at the destination being correct — same row count, same schema, fresh enough, free of nulls in critical columns, statistically consistent with what arrived yesterday.

Without explicit data quality checks, Pravah is a pipeline that blindly copies data. With them, it is a platform that guarantees data quality at the destination. The difference matters enormously to customers: a BI dashboard built on stale or corrupted data destroys trust faster than a failed pipeline.

Two related but distinct concerns need design:

**Data Quality Rules** — assertions that run against the data in transit or at the destination: row counts match, no nulls in `customer_id`, `revenue` is non-negative, source was last modified within 6 hours.

**Data Contracts** — a formal, versioned agreement between a data producer and a data consumer specifying the schema, SLA, and ownership of a dataset. A contract makes the producer accountable and gives the consumer a machine-checkable guarantee.

Both need to integrate with the DAG engine (ADR-018): a quality check is just another task type. Both need to integrate with the Metadata Service (ADR-019): violations are lineage events. Both need to feed the Agent Service (ADR-020): anomalies trigger auto-heal.

---

## Decision

Data quality and data contracts are first-class pipeline constructs in Pravah, implemented as specialized task types that run within the DAG engine.

**Quality Rule Categories:**

| Category | Examples | When It Runs |
|----------|---------|--------------|
| Freshness | `last_modified_at > NOW() - INTERVAL '6 hours'` | Before extraction (sensor task) |
| Schema | expected columns present, types match | After extraction, before load |
| Volume | row count between 1,000 and 10,000,000 | After extraction |
| Completeness | null rate < 5% on `customer_id` | After extraction |
| Value range | `age` between 0 and 150, `revenue` >= 0 | After extraction |
| Reconciliation | source row count == destination row count ± 0.1% | After load |
| Statistical | `avg(revenue)` within 3σ of 30-day baseline | After load |
| Custom SQL | `SELECT COUNT(*) FROM orders WHERE status IS NULL` == 0 | Configurable |

**Pipeline YAML representation:**

```yaml
steps:
  - id: check-freshness
    type: data_quality
    rule: freshness
    config:
      dataset: postgres://source/orders
      max_age_hours: 6
    on_failure: FAIL_PIPELINE

  - id: extract-orders
    type: extract
    depends_on: [check-freshness]

  - id: check-row-count
    type: data_quality
    rule: row_count
    config:
      min: 100
      max: 10000000
    on_failure: FAIL_PIPELINE

  - id: check-nulls
    type: data_quality
    rule: null_rate
    config:
      column: customer_id
      max_null_pct: 0.0
    on_failure: FAIL_PIPELINE

  - id: load-orders
    type: load
    depends_on: [check-row-count, check-nulls]

  - id: reconcile
    type: data_quality
    rule: row_reconciliation
    config:
      source_count_sql: "SELECT COUNT(*) FROM orders WHERE updated_at > :last_run"
      destination_count_sql: "SELECT COUNT(*) FROM dw.orders WHERE loaded_at > :last_run"
      tolerance_pct: 0.1
    depends_on: [load-orders]
    on_failure: ALERT_AND_CONTINUE
```

**`on_failure` strategies:**

- `FAIL_PIPELINE` — hard stop, entire execution fails, compensating actions triggered
- `ALERT_AND_CONTINUE` — emit alert event, mark step as WARNING, pipeline continues
- `RETRY` — retry the quality check N times before escalating
- `SKIP_DOWNSTREAM` — mark dependent steps as SKIPPED, continue parallel branches

**Data Contracts:**

A data contract is stored in the Metadata Service as a versioned document:

```yaml
# contract: orders-v2.yaml
contract_version: "2"
dataset: postgres://source/public/orders
owner: data-engineering@company.com
team: payments
sla:
  freshness_hours: 6
  availability_pct: 99.5
schema:
  - name: order_id
    type: UUID
    nullable: false
    description: "Primary key"
  - name: customer_id
    type: UUID
    nullable: false
  - name: revenue
    type: DECIMAL(12,2)
    nullable: false
    constraints:
      min: 0
  - name: status
    type: STRING
    nullable: false
    allowed_values: [PENDING, CONFIRMED, SHIPPED, CANCELLED]
quality_rules:
  - null_rate: { column: order_id, max_pct: 0.0 }
  - null_rate: { column: customer_id, max_pct: 0.0 }
  - value_range: { column: revenue, min: 0 }
change_policy: BACKWARD_COMPATIBLE   # or VERSIONED, BREAKING_REQUIRES_APPROVAL
```

When a pipeline subscribes to a dataset, it pins a contract version. If the producer changes the schema in a way that breaks the contract, CI validation catches it before merge.

**Quality results as Kafka events:**

Every quality rule execution emits an event to `data.quality.alerts`:

```json
{
  "pipeline_id": "...",
  "run_id": "...",
  "step_id": "check-nulls",
  "rule": "null_rate",
  "column": "customer_id",
  "result": "FAIL",
  "observed_null_pct": 0.0023,
  "threshold_null_pct": 0.0,
  "tenant_id": "megacorp",
  "timestamp": "2026-05-10T12:00:00Z"
}
```

Agent Service subscribes to `data.quality.alerts` and uses historical quality results to detect anomalies (e.g., null rate was 0.0% for 30 days, now it is 2.3% — likely a producer bug).

**Data Profiling:**

After every successful load, the Execution Service triggers a lightweight profiling task that computes column statistics (min, max, avg, null%, distinct count) and stores them in the Metadata Service. Profiling history is what makes statistical anomaly detection possible.

---

## Consequences

### Positive

- Quality is enforced at the pipeline level, not as an afterthought
- Contracts make producers accountable and give consumers machine-checkable guarantees
- Quality events feed the Agent Service for automated root cause analysis
- `on_failure: ALERT_AND_CONTINUE` allows pipelines to complete with warnings rather than blocking downstream consumers on non-critical columns

### Negative

- Quality steps add latency to pipeline execution (each check is an additional query)
- Statistical baseline checks require 30+ days of profiling history before they become meaningful
- Contract enforcement in CI requires that the Schema Registry and Metadata Service are part of the CI pipeline — adds CI complexity

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Quality checks overwhelm source database | Checks run against extracted data in-runner (DuckDB), not against source directly, except freshness checks which are lightweight COUNT/MAX queries |
| Contract version mismatch causes silent failures | CI validates contract compatibility on every merge to the pipeline repo; breaking changes require explicit version bump |
| Profiling adds significant post-load time | Profiling runs asynchronously in a separate task after the load task commits; does not block the pipeline run |

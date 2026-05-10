# ADR-017: Event Sourcing + CQRS for the Pipeline Service

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

The Pipeline Service manages the most important aggregate in Pravah: the pipeline definition. Every change to a pipeline — creation, step modification, trigger reconfiguration, version promotion, archival — must be:

1. **Auditable**: who changed what, when, and from which version?
2. **Time-travelable**: show me exactly what this pipeline looked like when it ran at 2am on 15 January
3. **Agent-readable**: the Agent Service needs the full change history to reason about why a pipeline is failing now versus last week
4. **Recoverable**: if a bad deployment corrupted the pipeline state, roll back to a known-good point

A standard CRUD model stores only the current state. Rolling back, auditing, and time-traveling all require either keeping audit tables in sync (fragile, often incomplete) or building change history as an afterthought. The Pipeline Service's data is inherently a sequence of changes — the current state is simply the result of applying all past changes.

Additionally, the Pipeline Service serves several very different read patterns:
- **Dashboard**: list pipelines with status, last run time, run count — needs a materialized summary
- **DAG builder UI**: the full pipeline definition with all step details — needs a complete snapshot
- **Scheduler**: the trigger configuration only — needs a lightweight projection
- **Agent Service**: the full event history for reasoning — needs the raw event stream
- **Elasticsearch catalog**: searchable pipeline metadata — needs a denormalized projection

No single table structure serves all these patterns optimally. CQRS separates the write model (events) from read models (purpose-built projections per use case).

---

## Decision

The Pipeline Service uses **Event Sourcing** for its write model and **CQRS** to maintain purpose-built read models.

**Core concepts:**

- **Aggregate**: `Pipeline` — identified by `pipeline_id`
- **Events**: every state change is an immutable event appended to the event store
- **Snapshots**: periodic materialization of aggregate state to avoid replaying all events on every load
- **Projections**: event consumers that maintain read-optimized views (PostgreSQL tables, Elasticsearch index)

**Event taxonomy:**

```
PipelineCreated
PipelineStepAdded
PipelineStepUpdated
PipelineStepRemoved
PipelineTriggerConfigured
PipelineVersionPublished     ← immutable snapshot of a version
PipelineActivated
PipelinePaused
PipelineArchived
PipelineOwnerChanged
PipelineTagsUpdated
PipelineParametersUpdated
```

**Event store schema:**

```sql
CREATE TABLE pipeline_events (
    id             BIGSERIAL    PRIMARY KEY,
    pipeline_id    UUID         NOT NULL,
    tenant_id      VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    event_version  INT          NOT NULL DEFAULT 1,
    sequence_num   BIGINT       NOT NULL,         -- monotonic per pipeline_id
    payload        JSONB        NOT NULL,
    metadata       JSONB        NOT NULL,         -- actor, ip, correlation_id
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_pipeline_events_seq
    ON pipeline_events (pipeline_id, sequence_num);

CREATE INDEX idx_pipeline_events_type
    ON pipeline_events (pipeline_id, event_type, occurred_at);

-- Snapshots to avoid full replay on load
CREATE TABLE pipeline_snapshots (
    pipeline_id    UUID         NOT NULL,
    sequence_num   BIGINT       NOT NULL,         -- snapshot taken after this event
    state          JSONB        NOT NULL,         -- full aggregate state
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pipeline_id, sequence_num)
);
```

**Command → Event → Projection flow:**

```
API receives: PUT /v1/pipelines/{id}/steps
    │
    ▼ Command: AddStepCommand
Execution Service
    │
    ├── Load aggregate: find latest snapshot + replay events since snapshot
    ├── Validate command against current state (e.g., no duplicate step names)
    ├── Apply command → produce PipelineStepAdded event
    ├── Append event to pipeline_events (optimistic lock: expected sequence_num)
    └── Publish event to Kafka topic: pipeline.events
                        │
          ┌─────────────┼────────────────────┐
          │             │                    │
          ▼             ▼                    ▼
  Pipeline Summary   DAG Projection      ES Catalog
  (PostgreSQL)       (PostgreSQL)        (Elasticsearch)
  for dashboard      for scheduler       for search
```

**Optimistic locking:**

```java
// Append event only if the current sequence_num matches expected
INSERT INTO pipeline_events (pipeline_id, sequence_num, ...)
VALUES (:pipelineId, :expectedSeqNum + 1, ...)
ON CONFLICT (pipeline_id, sequence_num) DO NOTHING
RETURNING id;
-- If 0 rows returned → concurrent modification detected → retry command
```

**Snapshot strategy:**

A snapshot is taken every 50 events per pipeline. On load:
1. Find the latest snapshot for the pipeline
2. Load all events with `sequence_num > snapshot.sequence_num`
3. Apply events on top of the snapshot state

Without snapshots, a pipeline with 500 events would require replaying all 500 on every load. With snapshots (every 50 events), the maximum replay is 49 events.

**Read models maintained by projections:**

| Projection | Store | Consumer | Purpose |
|------------|-------|----------|---------|
| `pipeline_summaries` | PostgreSQL | Dashboard query | Name, status, last run, run count |
| `pipeline_definitions` | PostgreSQL | Scheduler, DAG builder | Full step definitions, trigger config |
| `pipeline_catalog` | Elasticsearch | Search, data catalog | Full-text search, tag filtering |
| `pipeline_versions` | PostgreSQL | Version history UI | Immutable published versions |

Each projection is a Kafka consumer subscribed to `pipeline.events`. Projections are independently deployable and rebuildable — drop the table, replay events, projection is rebuilt.

---

## Consequences

### Positive

- **Complete audit trail is automatic**: every change to a pipeline is an event in the event store. The audit log is not a secondary concern — it is the primary data model. Time-travel debugging (show me this pipeline as it was at 2am on date X) is a query against the event store.
- **Agent Service has full reasoning context**: the Agent can replay a pipeline's event history to understand patterns — "this pipeline was modified 3 times in the last week and started failing after the second modification." This context is not reconstructible from a CRUD model.
- **Read models are optimized per consumer**: the Scheduler only queries `pipeline_definitions` for trigger config — a narrow, fast query. The dashboard queries `pipeline_summaries` — pre-aggregated, no joins. Each consumer gets exactly what it needs.
- **Projections are rebuildable**: if a bug corrupts the `pipeline_summaries` projection, it is rebuilt by replaying all events from the beginning. No data is lost — the event store is the source of truth.
- **Schema evolution at the event level**: adding a new field to a pipeline is a new event type (`PipelineTagsUpdated`), not an ALTER TABLE. Old events remain valid. New projections can choose to include or ignore the new field.

### Negative

- **Load complexity**: loading an aggregate requires reading a snapshot + events since the snapshot. Simple reads are no longer a single `SELECT * FROM pipelines WHERE id = ?`.
- **Eventual consistency for read models**: projections lag behind the event store by the Kafka consumer's processing delay (typically milliseconds). Immediately after a write, a read from the projection may return stale data.
- **Event schema evolution requires discipline**: once an event is stored, its type name and field names are permanent. Renaming `PipelineStepAdded.stepName` to `PipelineStepAdded.name` requires a migration strategy (versioned events, upcasters).
- **Higher initial implementation complexity**: the team must understand aggregates, commands, events, and projections as concepts before writing code. The learning curve is real.
- **Snapshot management**: the snapshot job must run reliably. If snapshots fall behind, load times grow until the next snapshot catches up.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Event store grows unboundedly | Monthly archival of events older than 2 years to MinIO as Parquet; event store retains last 2 years hot |
| Projection falls behind during high event volume | KEDA scales projection consumers based on `pipeline.events` Kafka lag; lag alert at >10,000 events |
| Event schema incompatibility after deployment | All events carry `event_version` field; upcasters transform old-schema events before processing |
| Optimistic lock contention on heavily-edited pipelines | Retry with exponential backoff (max 3 retries); conflicts are rare — pipeline edits are low-frequency |

---

## Alternatives Considered

### Standard CRUD with Audit Table

Maintain a `pipelines` table (current state) and a `pipeline_audit_log` table (populated by triggers or application code on every write).

Rejected because:
- Audit tables are secondary — if a bug skips the audit write, the audit is incomplete. With event sourcing, the audit IS the write — it is impossible to skip.
- The audit table cannot reconstruct point-in-time aggregate state without replaying the audit log — which is exactly what event sourcing provides, but with explicit design.
- Agent Service reasoning requires the full semantic history (what changed, not just what the row looked like before/after). JSONB diff in an audit table does not provide semantic event types.

### Append-Only Events Table Without Full Event Sourcing

Store events as a supplementary log alongside a traditional `pipelines` CRUD table. The `pipelines` table is the source of truth; events are for auditing only.

Rejected because:
- The dual-write problem: if the `pipelines` table update and the event insert are not in the same transaction, they can diverge. If they are in the same transaction, the event store is by definition the write-model, which is event sourcing.
- This is a compromised version of event sourcing that gives neither the simplicity of CRUD nor the full benefits of event sourcing.

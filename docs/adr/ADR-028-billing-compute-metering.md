# ADR-028: Billing & Compute Metering

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah is a SaaS platform that consumes real infrastructure resources on behalf of tenants: CPU time on cloud runners, network egress when moving data, storage for artifacts, and Kafka throughput for event processing. Without metering, Pravah cannot:

- **Enforce quotas** — prevent a single tenant from consuming disproportionate resources and starving others
- **Bill customers** — charge for usage beyond the plan tier rather than a flat monthly fee
- **Chargeback internally** — show engineering teams the cost of their pipelines
- **Capacity plan** — understand which tenants drive infrastructure spend

The metering system must be accurate to the minute level (billing), available in near-real-time (quota enforcement), and queryable for arbitrary historical ranges (invoicing, analytics).

---

## Decision

A dedicated **Billing Service** handles compute metering, quota enforcement, and invoice generation.

**Compute unit model:**

All resource usage is normalized to a single unit: **Compute Unit (CU)**. This simplifies billing (one number on the invoice) while capturing real cost.

| Resource | 1 CU = |
|----------|--------|
| Runner CPU | 1 vCPU-minute |
| Runner memory | 2 GB-minute |
| Data transferred | 1 GB (network egress) |
| Artifact storage | 10 GB-day |
| Kafka events | 1 million events |

A job that uses 2 vCPUs for 5 minutes and transfers 3 GB consumes: `(2 × 5) + 3 = 13 CU`.

**Metering event flow:**

```
Runner reports resource usage via gRPC stream
          │
          ▼
Runner Service aggregates per-job usage metrics
          │
          │  Publishes to Kafka: billing.compute.used
          ▼
Billing Service (Kafka consumer)
          │
          │  1. Writes to billing_events table (partitioned by month)
          │  2. Updates tenant's running CU total in Redis (real-time quota)
          │  3. Checks quota: if over limit, publish quota.exceeded event
          ▼
          │
          ├──► quota.exceeded → Execution Service pauses new job dispatch for tenant
          └──► End of month: aggregate billing_events → generate invoice
```

**Kafka event schema (`billing.compute.used`):**

```json
{
  "tenant_id": "megacorp",
  "pipeline_id": "...",
  "execution_id": "...",
  "job_id": "...",
  "runner_id": "...",
  "runner_type": "SELF_HOSTED | CLOUD | EPHEMERAL",
  "period_start": "2026-05-10T12:00:00Z",
  "period_end": "2026-05-10T12:05:00Z",
  "cpu_vcpu_minutes": 10.0,
  "memory_gb_minutes": 4.0,
  "data_transferred_gb": 3.0,
  "compute_units": 13.0
}
```

**Database schema (`billing_db`):**

```sql
-- Partitioned monthly — DROP old partitions for cold storage archival
CREATE TABLE billing_events (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)  NOT NULL,
    pipeline_id     UUID          NOT NULL,
    execution_id    UUID          NOT NULL,
    job_id          UUID          NOT NULL,
    runner_type     VARCHAR(20)   NOT NULL,
    period_start    TIMESTAMPTZ   NOT NULL,
    compute_units   DECIMAL(12,4) NOT NULL,
    cpu_vcpu_min    DECIMAL(12,4),
    memory_gb_min   DECIMAL(12,4),
    data_gb         DECIMAL(12,4),
    PRIMARY KEY (id, period_start)
) PARTITION BY RANGE (period_start);

-- Quota ledger — current cycle usage (updated in real-time from Redis)
CREATE TABLE tenant_quota_ledger (
    tenant_id         VARCHAR(255) PRIMARY KEY,
    plan_tier         VARCHAR(50)  NOT NULL,  -- FREE, PROFESSIONAL, ENTERPRISE
    monthly_cu_limit  DECIMAL(12,2),          -- NULL = unlimited
    current_cycle_cu  DECIMAL(12,4) NOT NULL DEFAULT 0,
    cycle_start       DATE          NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL
);
```

**Real-time quota enforcement (Redis):**

The quota ledger in PostgreSQL is the authoritative source but is updated in batch (every 5 minutes). Redis holds the live running total:

```
Key: quota:tenant:{tenantId}:cycle:{YYYY-MM}
Type: INCRBYFLOAT
TTL: end of billing cycle
```

When a new job is about to be dispatched, the Execution Service checks:

```java
double currentUsage = redis.get("quota:tenant:" + tenantId + ":cycle:" + cycle);
double planLimit = tenantConfig.getMonthlyComputeUnitLimit();
if (planLimit > 0 && currentUsage >= planLimit) {
    throw new QuotaExceededException("Monthly compute quota exhausted");
}
```

This is a best-effort check (Redis eventual consistency) — exact enforcement uses the PostgreSQL ledger for invoice disputes.

**Plan tiers:**

| Tier | Monthly CU Limit | Runner Types | Support |
|------|-----------------|--------------|---------|
| Free | 100 CU | Cloud only | Community |
| Professional | 5,000 CU | Cloud + Self-hosted | Email (24h SLA) |
| Enterprise | Unlimited | All types | Dedicated CSM |

**Invoice generation:**

At the end of each billing cycle, the Billing Service:
1. Aggregates `billing_events` for the tenant's cycle date range
2. Applies plan tier: usage within included CU = $0; overage = $price_per_cu
3. Generates an invoice record in `invoices` table
4. Publishes `invoice.generated` event → triggers Notification Service (email delivery)

---

## Consequences

### Positive

- Single CU metric simplifies billing communication to customers — one number, not a complex matrix of CPU + memory + egress
- Real-time Redis quota check prevents runaway jobs from exhausting the month's budget before the invoice is generated
- Monthly table partitioning makes end-of-cycle aggregations fast (scan one partition) and makes archival simple (DROP partition after 13 months)
- Metering data per pipeline enables per-pipeline cost reporting in the UI — teams can see which pipelines are expensive

### Negative

- CU normalization is approximate — a memory-intensive job on a high-memory runner costs the same CUs as a CPU-intensive job. Some customers will feel this is unfair.
- Self-hosted runners run on customer infrastructure. Pravah cannot meter actual resource consumption on customer machines directly — metering relies on what the runner self-reports. A dishonest runner binary could under-report.
- Quota enforcement is best-effort at job dispatch time — a job that starts within quota can still exceed quota during execution if it runs longer than expected.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Runner under-reports usage | Billing audits compare reported usage against actual duration × job config; anomalies flagged for manual review |
| Redis quota state lost on restart | Redis persistence (AOF) plus a reconciliation job that re-computes current_cycle_cu from billing_events at Redis startup |
| Monthly aggregation slow for high-volume tenants | billing_events partitioned by month; aggregation scans only current partition; index on (tenant_id, period_start) |
| Billing disputes | billing_events is append-only and immutable; dispute resolution uses raw events, not derived totals |

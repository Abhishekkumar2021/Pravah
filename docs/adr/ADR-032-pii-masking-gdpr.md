# ADR-032: PII Masking & GDPR Data Flow Controls

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah pipelines process customer data — often data that contains PII (Personally Identifiable Information): email addresses, names, national ID numbers, phone numbers, credit card numbers, financial records. This data flows through multiple systems in Pravah's architecture:

- **Execution logs**: job logs capture inputs and outputs for debugging. If a pipeline step processes a CSV with 50,000 customer records, those records can appear in logs verbatim.
- **Artifact storage (MinIO)**: intermediate pipeline outputs, input snapshots, and error dumps are stored in MinIO. These may contain raw PII.
- **Data lineage (Elasticsearch)**: lineage records track which columns fed which tables. Column names like `email`, `ssn`, `credit_card` appear in the lineage graph.
- **Kafka events**: `job.status.updated` events may carry sample rows from failed jobs (for debugging in the Agent Service).
- **Agent Service context**: when the Agent Service reasons about a failure, it may pull execution logs or artifact samples — which could contain PII.
- **Notification Service**: failure notifications may include log excerpts containing PII.

GDPR (EU), CCPA (California), LGPD (Brazil), and similar regulations create obligations:

1. **Data minimization**: do not collect or store PII beyond what is necessary for the stated purpose.
2. **Right to erasure**: when a customer requests deletion, PII must be removed from all stores — including logs, lineage, and artifacts — not just the primary database.
3. **Purpose limitation**: data collected for pipeline execution debugging should not be used for other purposes (e.g., analytics).
4. **Cross-border transfer controls**: data processed by a European customer's pipeline must not leave the EU region without explicit consent.

Without explicit PII controls, Pravah risks being a compliance liability for its customers — which blocks enterprise sales and creates regulatory exposure.

---

## Decision

**Column-level PII tagging in pipeline definitions, with automatic masking at all egress points.**

### 1. PII Declaration in Pipeline Definition

Pipeline authors declare which columns contain PII in the pipeline YAML/JSON definition:

```yaml
pipeline:
  id: orders-etl
  steps:
    - id: extract
      source: crm_database
      columns:
        - name: order_id
        - name: customer_email
          pii: true
          pii_class: EMAIL
          masking: HASH          # SHA-256, consistent for join-ability
        - name: customer_name
          pii: true
          pii_class: NAME
          masking: REDACT        # replace with "[REDACTED]"
        - name: credit_card_last4
          pii: true
          pii_class: FINANCIAL
          masking: TOKENIZE      # replace with format-preserving token
        - name: order_amount
          # not PII — no tag required
```

The Pipeline Service validates and stores PII declarations as part of the pipeline schema. They are versioned with the pipeline definition.

**Masking strategies:**

| Strategy | Result | When to Use |
|----------|--------|------------|
| `REDACT` | `[REDACTED]` | Data only needed for debugging presence, not value |
| `HASH` | `sha256(value)` | Joining on PII across datasets (consistent across calls) |
| `TOKENIZE` | Reversible format-preserving token | Downstream processes need consistent ID but not the PII |
| `PARTIAL` | `j***@example.com` | User-facing display where hint is needed |

### 2. Runner-Side Masking (at the source)

The Runner executes masking **before** any data leaves the job step boundary. PII columns are masked before being written to:
- The job's output artifact (MinIO)
- The execution log
- Any sample row included in status events

```java
// Runner SDK — PostStepProcessor
public class PiiMaskingProcessor implements PostStepProcessor {
    @Override
    public StepOutput process(StepOutput output, PipelineStep stepDefinition) {
        List<PiiColumnConfig> piiColumns = stepDefinition.getPiiColumns();
        if (piiColumns.isEmpty()) return output;

        return output.withMaskedRows(row -> {
            for (PiiColumnConfig col : piiColumns) {
                row.set(col.getName(), maskingStrategy(col.getMasking()).mask(row.get(col.getName())));
            }
            return row;
        });
    }
}
```

Masking on the runner ensures PII never reaches the cloud control plane — even for on-premise runners. The cloud only sees masked data.

### 3. Log Scrubbing (defense-in-depth)

Even with runner-side masking, free-text log lines from customer code can leak PII. A log scrubber runs as part of the log ingestion pipeline in the ELK stack:

- Pattern-based redaction for common PII patterns: email regex, credit card Luhn patterns, IPv4/IPv6 addresses (configurable per tenant).
- Column-name-based redaction: if a log line contains a JSON field whose key matches a declared PII column name, the value is redacted before indexing.

This is defense-in-depth, not the primary control. Runner-side masking is the primary control.

### 4. Right to Erasure — Data Subject Deletion

When a customer submits a right-to-erasure request for a specific user's data, the Delete Job workflow covers:

```
1. Tenant admin submits erasure request via API: 
   POST /v1/privacy/erasure-requests
   { "subject_identifier": "user@example.com", "identifier_type": "EMAIL" }

2. Privacy Controller creates an ErasureRequest record with:
   - subject_identifier (hashed for storage — do not store plaintext PII in the request)
   - affected_pipelines (pipelines that declared EMAIL as a PII column)
   - status: PENDING

3. Erasure workers process each store:
   a. MinIO: scan artifact objects for the pipeline + time range, nullify/delete PII fields
   b. Elasticsearch (logs): delete log entries by execution_id that involved this subject
      (identified via a PII column value hash index built at ingest time)
   c. Kafka: PII is in transit only; events older than retention (7 days) are naturally deleted
   d. PostgreSQL: if raw values were stored in sample_rows (execution metadata), nullify them
   e. Lineage graph: PII class tags remain; specific values never stored in lineage

4. ErasureRequest marked COMPLETED with affected records count.
   Completion timestamp stored as proof of deletion.
```

The key constraint: **PII values are never stored in the lineage graph, Kafka event bodies beyond 7 days, or the control plane database tables directly**. The lineage records that a column `customer_email` of type `EMAIL` was used — not the actual email values.

### 5. Cross-Border Data Transfer Controls

PII column declarations feed into region enforcement:

- Pipelines with PII columns tagged `GDPR_RESTRICTED: true` can only be assigned to runners in approved regions (EU, EEA).
- The Scheduler Service checks region affinity before assigning jobs to runners.
- Runners registered in non-approved regions are excluded from the candidate pool for restricted pipelines.

```sql
-- Runner registration includes region
INSERT INTO runners (id, tenant_id, region, gdpr_approved_regions)
VALUES (..., 'eu-west-1', ARRAY['EU', 'EEA']);

-- Scheduler query respects PII region constraints
SELECT * FROM runners
WHERE tenant_id = :tenantId
  AND status = 'AVAILABLE'
  AND (
    :pipelineRequiresGdprRegion = false
    OR 'EU' = ANY(gdpr_approved_regions)
  );
```

---

## Consequences

### Positive

- **Compliance by design**: PII masking is declared in the pipeline definition and enforced by the platform — not left to pipeline authors to implement manually in their transformation code.
- **Auditable**: PII column declarations are versioned with the pipeline definition. An auditor can inspect exactly which columns were declared as PII and what masking strategy was applied for any historical run.
- **Right to erasure is tractable**: because PII values are masked at the source and never stored verbatim in control-plane stores (logs have limited field-value storage; lineage has no values), erasure scope is manageable.
- **Defense-in-depth**: runner masking + log scrubbing + column-name-based redaction means three layers must fail simultaneously for PII to appear in control-plane logs.
- **No PII in cloud for on-premise runners**: the cloud control plane is physically separated from raw data. This is a strong argument in enterprise security reviews.

### Negative

- **Pipeline author responsibility**: PII masking only works if pipeline authors correctly declare PII columns. An author who forgets to tag `ssn` as PII receives no automatic protection. This requires training and possibly static analysis tooling to detect likely-PII column names in pipeline definitions.
- **Hash masking breaks readability for debugging**: `sha256("user@example.com")` in a log is not useful for debugging a specific user's issue. Engineers must use the Privacy API to look up which hash corresponds to a subject.
- **Erasure is best-effort for Kafka**: Kafka messages older than the retention period are naturally deleted. Messages within the retention window are immutable — individual records cannot be deleted from a Kafka topic. Erasure for in-retention Kafka data requires compacted topics or accepting a bounded delay (7-day max for erasure of in-flight data).
- **Performance overhead for tokenization**: format-preserving tokenization (Vault Transit engine) adds a per-row encryption call. For pipelines processing millions of rows, this may require batched tokenization to stay within latency budgets.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Pipeline author fails to tag a PII column | Static analysis on pipeline definition at save time: warn if column name matches known PII patterns (`email`, `phone`, `ssn`, `dob`, `credit_card`, etc.) |
| Log scrubber regex has false positives (redacts non-PII) | Scrubber patterns are configurable per tenant; false positive rate monitored via Prometheus counter |
| Erasure request misses a store | Erasure workflow generates a checklist; incomplete workflows alert on-call; stores have a 30-day compliance SLA |
| Hashed values enable re-identification via rainbow tables | HMAC-SHA256 with a per-tenant secret (stored in Vault) instead of plain SHA-256 — makes rainbow tables infeasible |
| Cross-border data transfer for multi-region runners | Scheduler enforces region affinity at assignment time; region mismatch = job never assigned to non-compliant runner |

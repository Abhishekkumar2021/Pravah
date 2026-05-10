# ADR-021: MinIO for Artifact Storage

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's runners produce output beyond database writes. Every pipeline execution generates artifacts that must be stored durably and made accessible to subsequent steps, users, and the platform itself:

- **Transformed data files**: Parquet files produced by DuckDB transforms (input to the next step or final destination)
- **Step outputs for fan-in joins**: Step A produces a file; Steps B and C both need to read it
- **Job logs**: full log output from each job execution (too large for PostgreSQL, too structured for ELK)
- **Data profiling reports**: column statistics, row counts, null percentages per run
- **Flink checkpoints**: state snapshots for fault-tolerant stream processing
- **Cold storage archives**: monthly PostgreSQL partition exports as Parquet (see ADR-003)
- **Pipeline exports**: YAML pipeline definitions exported by users for GitOps workflows

The control plane (Kubernetes services) and runners (customer infrastructure) both need access. A runner in a customer's data center must be able to upload a 50GB Parquet file and have the next step (possibly a different runner) download it. This requires a network-accessible object store, not a local filesystem or a database blob column.

The object store must be **S3-compatible** — this is the de facto standard API for object storage, supported by all major runtimes (DuckDB, Spark, Flink, AWS SDK, MinIO SDK).

---

## Decision

**MinIO** (S3-compatible object storage) is the artifact store for all Pravah pipeline artifacts.

**Why MinIO over direct S3:**
- MinIO can be deployed on-premise (critical for customers with data sovereignty requirements) and in any cloud. A dependency on AWS S3 locks Pravah into AWS.
- MinIO is fully S3-compatible — every SDK that works with S3 works with MinIO. No code changes when switching.
- MinIO's performance for large files (multi-part upload, parallel transfer) matches S3 for Pravah's use cases.

**Bucket layout:**

```
pravah-artifacts/
├── tenants/{tenant_id}/
│   ├── executions/{execution_id}/
│   │   ├── steps/{step_id}/
│   │   │   ├── output/
│   │   │   │   ├── data.parquet          ← step output data
│   │   │   │   └── data.parquet.metadata ← schema + row count
│   │   │   ├── logs/
│   │   │   │   └── execution.log.gz      ← compressed step log
│   │   │   └── profile/
│   │   │       └── profile.json          ← data quality profile
│   │   └── manifest.json                 ← links all step outputs
│   ├── archives/
│   │   └── {year}/{month}/
│   │       └── {table_name}_{partition}.parquet  ← cold archival
│   └── exports/
│       └── pipelines/{pipeline_id}-v{n}.yaml     ← pipeline YAML export
├── flink-checkpoints/
│   └── {job_id}/{checkpoint_id}/        ← Flink state snapshots
└── platform/
    └── runner-jars/
        └── runner-{version}.jar          ← runner binary distribution
```

**Access pattern:**

Runners do not get long-lived MinIO credentials. They receive pre-signed URLs from the Execution Service for each artifact they need to upload or download:

```
Runner completes step → calls Execution Service: "give me an upload URL for step output"
Execution Service → calls MinIO: generate pre-signed PUT URL (15-min TTL) for
    tenants/{tenant_id}/executions/{exec_id}/steps/{step_id}/output/data.parquet
Execution Service → returns pre-signed URL to runner
Runner → uploads directly to MinIO via the pre-signed URL (no credentials on runner)

Next step runner → calls Execution Service: "give me download URL for step X output"
Execution Service → generates pre-signed GET URL → returns to runner
Runner → downloads directly from MinIO
```

Pre-signed URLs with short TTL mean:
- Runners never hold MinIO credentials — only the Execution Service does
- A compromised runner can use a pre-signed URL for at most 15 minutes
- Tenant isolation: the Execution Service only generates URLs within the tenant's prefix

**Lifecycle policies:**

```yaml
# MinIO lifecycle rule per bucket
- id: "delete-step-artifacts-after-30-days"
  filter:
    prefix: "tenants/"
  expiration:
    days: 30    # step outputs, logs deleted after 30 days

- id: "delete-archives-after-2-years"
  filter:
    prefix: "tenants/*/archives/"
  expiration:
    days: 730

- id: "keep-runner-jars-indefinitely"
  filter:
    prefix: "platform/runner-jars/"
  # no expiration
```

**High availability:**

MinIO runs in distributed mode with erasure coding across 4 nodes. Erasure coding with 4 drives provides tolerance for 1 drive failure with no data loss and continued operation with up to 2 drive failures (degraded mode). This matches the resilience of a 3-replica PostgreSQL cluster.

```
4 nodes × drives = N+M erasure coding
Minimum N drives available for reads
All N+M drives needed for writes in normal mode
```

**DuckDB integration on runner:**

```java
// DuckDB on the runner reads from and writes to MinIO via S3 API
Properties duckdbProps = new Properties();
duckdbProps.setProperty("s3_endpoint", "minio.pravah.svc.cluster.local:9000");
duckdbProps.setProperty("s3_url_style", "path");
// Credentials: from pre-signed URL (no static credentials on runner)

// Read previous step's Parquet output
conn.execute("""
    CREATE TABLE input AS
    SELECT * FROM read_parquet('s3://pravah-artifacts/tenants/.../output/data.parquet')
""");

// Write transform output as Parquet
conn.execute("""
    COPY (SELECT ...) TO 's3://pravah-artifacts/tenants/.../output/data.parquet'
    (FORMAT PARQUET, COMPRESSION ZSTD)
""");
```

---

## Consequences

### Positive

- **On-premise deployable**: MinIO runs anywhere — customer data centers, air-gapped environments, any cloud. No cloud vendor lock-in.
- **S3-compatible**: DuckDB, Spark, Flink, and all AWS SDKs work without modification. Switching between MinIO and S3 is a configuration change.
- **Pre-signed URLs eliminate credential distribution**: runners never hold static MinIO credentials. Credentials are centralized in the Execution Service (fetched from Vault). A compromised runner's pre-signed URL expires in 15 minutes.
- **Tenant isolation via path prefix**: all runner uploads are within `tenants/{tenant_id}/`. The Execution Service enforces the prefix before generating URLs. A runner cannot request a URL for another tenant's prefix.
- **Lifecycle policies automate cleanup**: step artifacts, logs, and profiles are automatically deleted after 30 days. No manual cleanup job required.

### Negative

- **MinIO cluster is another operational dependency**: a MinIO cluster requires monitoring, capacity planning, and upgrade management. At 4 nodes, it is not trivial.
- **Pre-signed URL management adds latency**: each step upload/download requires a round-trip to the Execution Service to get a URL before the actual transfer begins. This adds ~5ms per artifact access.
- **Erasure coding requires minimum 4 nodes**: a 2-node MinIO deployment does not provide the redundancy guarantees. The minimum operational footprint is fixed.
- **Large artifact transfers bypass Kubernetes networking**: when a runner in customer infrastructure downloads a large (50GB+) Parquet file, the transfer goes over the public internet (or VPN). Transfer time is bounded by the customer's network bandwidth.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| MinIO node failure during multi-part upload | Erasure coding tolerates 1 node failure; multi-part uploads resume from last successful part |
| Pre-signed URL leaked in logs | URLs are never logged; they appear in runner memory only; 15-min TTL limits exposure |
| Storage exhaustion | MinIO storage usage metric in Prometheus; alert at 80% capacity; lifecycle policies prevent unbounded growth |
| Runner uploads to wrong tenant prefix | Execution Service validates `execution_id` belongs to requesting tenant before generating URL |

---

## Alternatives Considered

### AWS S3 Directly

Use Amazon S3 as the artifact store. Fully managed, no operational overhead.

Rejected as the primary store because:
- On-premise Pravah deployments cannot use AWS S3
- Enterprise customers with data sovereignty requirements (EU GDPR, financial regulators) may be prohibited from storing data in US cloud regions
- MinIO's S3 compatibility means the same code works with both — customers who want S3 can configure MinIO to point at S3 as a backend (MinIO supports this via gateway mode)

### PostgreSQL Large Objects (BYTEA / LO)

Store artifacts as binary columns in PostgreSQL.

Rejected because:
- PostgreSQL is not designed for storing large files. A 50GB Parquet file as a BYTEA column would cause catastrophic bloat.
- VACUUM on large object columns is slow and disruptive.
- No streaming upload/download — the entire file must pass through the PostgreSQL wire protocol.
- No lifecycle management, no erasure coding, no multi-part upload.

### Shared NFS / Network Filesystem

Mount a shared NFS volume on all Kubernetes nodes and runner VMs.

Rejected because:
- NFS does not work across organizational boundaries (customer VM to cloud). Runners in customer networks cannot mount the cloud's NFS.
- NFS is not designed for object storage semantics (key-value, metadata, lifecycle).
- NFS at scale has well-known performance and reliability problems.

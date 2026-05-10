# ADR-006: Pool Model for Multi-Tenancy

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah is a multi-tenant SaaS platform. Multiple organizations (tenants) use the same deployment. The multi-tenancy strategy determines how tenant data and compute are isolated from each other, and has major implications for:

- **Security**: can Tenant A access Tenant B's data?
- **Cost efficiency**: are infrastructure resources shared or dedicated?
- **Operational complexity**: how many databases, clusters, and deployments are managed?
- **Customization**: can tenants have different configurations, rate limits, or features?
- **Compliance**: do regulatory requirements (e.g., GDPR, HIPAA, SOC 2) require physical data separation?

The two ends of the spectrum are:

**Silo model**: each tenant gets a completely isolated deployment — their own Kubernetes namespace (or cluster), their own database, their own Kafka cluster. Full isolation by default. Extremely expensive to operate at scale. Every new tenant requires provisioning a new stack.

**Pool model**: all tenants share the same infrastructure. All pipelines run in the same Kubernetes cluster; all tenant data lives in the same PostgreSQL tables, distinguished by a `tenant_id` column. Far more efficient to operate. Isolation must be enforced in software.

Pravah's primary market is engineering teams at mid-to-large companies. The platform must be cost-efficient enough that small teams can afford it, but secure enough that enterprise customers trust it with their data pipelines.

---

## Decision

**Default deployment**: pool model — all tenants share the same application cluster, Kafka cluster, and PostgreSQL cluster. Tenant data is logically isolated by `tenant_id` enforced at multiple layers.

**Enterprise upgrade path**: silo model — large enterprise customers with strict compliance requirements (HIPAA, data residency, contract-mandated isolation) can be provisioned as a dedicated stack. This is a commercial and operational decision, not a code change.

**Isolation layers in the pool model:**

```
Layer 1: Application layer
  Every API endpoint extracts tenant_id from the JWT token
  and passes it as a parameter to all repository calls.
  No query runs without a tenant_id filter.

Layer 2: Repository layer
  All Spring Data repositories use tenant-scoped methods:
  findByIdAndTenantId(), deleteByIdAndTenantId(), etc.
  Generic findById() is not exposed from service code.

Layer 3: Database layer — PostgreSQL Row-Level Security
  RLS policies enforce that even if application code
  forgets to include tenant_id, the query returns nothing.
  SET LOCAL pravah.current_tenant_id = 'megacorp'
  before every transaction. Policies applied as FORCE ROW LEVEL SECURITY.
  (Detailed in ADR-013)

Layer 4: Kafka — tenant_id in every event payload
  All Kafka events carry tenant_id in the message payload.
  Consumer base class validates tenant context on every message.

Layer 5: Per-tenant encryption keys (Vault Transit)
  Sensitive fields (secrets, credentials, pipeline output)
  are encrypted with per-tenant keys managed in HashiCorp Vault.
  Key compromise for one tenant does not affect others.
```

**Tenant context propagation:**

```
Request arrives at API Gateway
    │
    ├── JWT validated: extract { sub, tenant_id, roles }
    │
    ├── TenantContext.set(tenantId) — ThreadLocal
    │
    ├── All downstream calls carry X-Tenant-ID header
    │
    ├── Kafka messages carry tenant_id in payload
    │
    └── Database session: SET LOCAL pravah.current_tenant_id = ?
```

**Resource isolation within the pool:**

Even in the pool model, tenants must not starve each other. Resource isolation is applied at:

- **API rate limiting**: per-tenant rate limits enforced at the API Gateway (Redis token bucket, ADR-012)
- **Kafka consumer isolation**: pipeline triggers for Tenant A do not delay processing for Tenant B. Job partitioning by `tenant_id` hash ensures no single consumer is monopolized by one tenant.
- **Job queue priority**: enterprise tenants may be assigned higher job queue priority. The scheduler selects jobs in priority order, not pure FIFO.
- **Runner assignment**: enterprise tenants can specify dedicated runner labels, ensuring their jobs run on runners they control.

---

## Consequences

### Positive

- **Cost efficiency**: shared infrastructure means 10,000 tenants can run on a cluster sized for a fraction of 10,000 dedicated stacks. Kubernetes bin-packing and Kafka partition sharing make this possible.
- **Operational simplicity**: one cluster to manage, monitor, and patch. Schema migrations run once. Observability dashboards cover all tenants.
- **Fast tenant onboarding**: provisioning a new tenant is a database insert + Vault key creation. It takes seconds, not hours. No new infrastructure is provisioned.
- **Uniform feature rollout**: a new feature deployed to the cluster is available to all tenants immediately. No per-tenant deployment required.
- **Multi-layer defense**: the 5-layer isolation model means a bug at one layer (e.g., a service forgets to include `tenant_id` in a query) does not result in data leakage because RLS at the database layer enforces it independently.

### Negative

- **Noisy neighbor risk**: a tenant with very large pipelines (many jobs, high event volume) can consume Kafka partitions, PostgreSQL IOPS, and CPU time that degrades other tenants. Mitigated by rate limiting and job queue management, but requires active monitoring.
- **Compliance complexity**: some regulations (GDPR Article 25, HIPAA) may require demonstrating data isolation. Logical isolation via RLS is usually acceptable with appropriate controls and audit trails, but some auditors require physical separation.
- **Blast radius**: a critical bug in the application layer (e.g., a missing `tenant_id` filter) could potentially expose data across tenants. The RLS layer catches this, but the defense-in-depth must be maintained rigorously.
- **Schema constraints**: the `tenant_id` column must be present on every table. This is enforced by convention and code review, not enforced by the database automatically.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Application bug leaks cross-tenant data | PostgreSQL RLS is the backstop; unit tests enforce tenant-scoped repository methods; automated tests verify isolation |
| One tenant exhausts database connections | PgBouncer connection pool limits per connection source; per-tenant connection tracking for large tenants |
| GDPR data residency requirement | Data residency is a deployment topology concern; for EU-only tenants, a dedicated EU cluster is provisioned (silo model for specific regions) |
| Vault key rotation for one tenant | Vault's Transit engine supports per-key rotation without affecting other tenants' keys |

---

## Alternatives Considered

### Silo Model as Default

Give every tenant a dedicated Kubernetes namespace with its own PostgreSQL instance, Kafka cluster, and application deployment.

Rejected as default because:
- The operational overhead is multiplicative. 1000 tenants = 1000 PostgreSQL instances to patch, monitor, and back up.
- New tenant onboarding requires infrastructure provisioning, which takes minutes at best (Terraform apply + Kubernetes namespace setup) and introduces provisioning failure as a customer-facing concern.
- Per-tenant Kafka clusters are extremely expensive — Kafka's minimum viable deployment is 3 brokers × 3 replicas.
- Most Pravah tenants do not have compliance requirements that mandate physical separation. The pool model with strong software isolation is sufficient.

### Schema-per-Tenant

Each tenant gets their own PostgreSQL schema (`megacorp.jobs`, `startupxyz.jobs`) within a shared PostgreSQL cluster. This provides schema-level isolation without Row-Level Security complexity.

Rejected because:
- PostgreSQL does not efficiently support tens of thousands of schemas in a single cluster. Schema metadata bloat degrades planning time for all queries.
- Running Flyway migrations for every tenant schema creates an O(n) migration cost as the tenant count grows.
- Connection pooling with PgBouncer in transaction mode does not support `SET search_path` for schema routing — PgBouncer resets session-level settings between transactions.
- Row-Level Security provides equivalent (and stronger) isolation without the schema management overhead.

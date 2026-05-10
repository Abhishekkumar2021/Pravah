# ADR-013: PostgreSQL Row-Level Security for Tenant Data Isolation

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah uses the pool multi-tenancy model (ADR-006): all tenants share the same PostgreSQL schema, distinguished by a `tenant_id` column on every table. Tenant isolation is the most critical security property in the system — no tenant must ever be able to read, write, or delete another tenant's data.

The primary isolation mechanism is application-layer filtering: every repository method includes `AND tenant_id = ?` in its query. This works correctly when the code is written correctly. But software has bugs. A developer adds a new admin endpoint that calls `findById()` instead of `findByIdAndTenantId()`. A refactoring silently removes a tenant filter. A new service incorrectly constructs a query.

The consequence of a single missed tenant filter is a cross-tenant data leak — a serious security incident that violates customer trust and potentially regulatory obligations (GDPR, SOC 2 Type II). Application-layer filtering alone is insufficient as a security guarantee. Defense in depth requires a layer that enforces tenant isolation regardless of what the application code does.

PostgreSQL Row-Level Security (RLS) is that layer.

---

## Decision

PostgreSQL Row-Level Security is enabled with `FORCE ROW LEVEL SECURITY` on all tenant-scoped tables. RLS policies enforce that every query — regardless of what the application code includes — returns only rows belonging to the current tenant.

**Setup per table:**

```sql
-- Enable RLS and force it even for table owners
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs FORCE ROW LEVEL SECURITY;

-- Policy: a row is visible only if its tenant_id matches the session variable
CREATE POLICY tenant_isolation_policy ON jobs
    USING (tenant_id = current_setting('pravah.current_tenant_id', TRUE));

-- Same pattern for every tenant-scoped table
ALTER TABLE executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE executions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON executions
    USING (tenant_id = current_setting('pravah.current_tenant_id', TRUE));
```

The second argument `TRUE` to `current_setting()` means: return `NULL` (rather than throwing an error) if the variable is not set. The `USING` clause evaluates to `NULL = NULL` → `FALSE`, so if a query runs without setting the tenant context, it returns zero rows rather than throwing an exception. This is the safe failure mode — returning nothing rather than returning everything.

**Setting tenant context per transaction:**

```java
@Transactional
public Job getJob(String jobId) {
    // Set the RLS variable at the start of every transaction
    entityManager.createNativeQuery(
        "SET LOCAL pravah.current_tenant_id = :tenantId"
    ).setParameter("tenantId", TenantContext.getCurrentTenantId())
     .executeUpdate();

    // This query is now automatically filtered by RLS
    // Even if we wrote "SELECT * FROM jobs WHERE id = ?" with no tenant filter,
    // RLS would restrict results to the current tenant's rows
    return jobRepository.findById(jobId).orElseThrow();
}
```

`SET LOCAL` scopes the variable to the current transaction. When the transaction ends (commit or rollback), the variable is cleared. With PgBouncer in transaction mode, each transaction is a separate unit from PgBouncer's perspective, so `SET LOCAL` variables do not leak across transactions from different tenants sharing a connection.

**Database user separation:**

The application connects to PostgreSQL as the `pravah_app` role, not as `postgres` or a table owner. RLS with `FORCE ROW LEVEL SECURITY` applies to all roles, including superusers, when `FORCE` is used. Internal admin operations (migrations, maintenance) use a separate `pravah_admin` role with explicit RLS bypass:

```sql
-- pravah_admin can bypass RLS for maintenance
ALTER ROLE pravah_admin BYPASSRLS;

-- pravah_app cannot bypass RLS (default behavior)
-- FORCE ROW LEVEL SECURITY means even table owners cannot bypass
```

**Insert policies (preventing cross-tenant writes):**

The `USING` clause in the policy applies to SELECT, UPDATE, and DELETE. For INSERT, a `WITH CHECK` clause ensures new rows cannot be inserted with a different tenant's `tenant_id`:

```sql
CREATE POLICY tenant_isolation_policy ON jobs
    USING (tenant_id = current_setting('pravah.current_tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', TRUE));
```

An INSERT that tries to write a row with `tenant_id = 'other-tenant'` when the session is set to `'megacorp'` will fail with a policy violation error.

---

## Consequences

### Positive

- **Defense in depth**: even if every layer of the application stack has a bug, the database enforces tenant isolation. A query without a `tenant_id` filter returns zero rows — not cross-tenant data.
- **Transparent to the ORM**: once `SET LOCAL pravah.current_tenant_id` is in place at the start of each transaction, Spring Data JPA / Hibernate queries do not need to include `tenant_id` filters — RLS applies them automatically. This simplifies repository code.
- **Detects application bugs silently**: if a developer forgets to add a tenant filter, they discover it when their query returns no results — a functional bug that is caught during development and testing, not a security bug in production.
- **PostgreSQL native**: RLS is a built-in PostgreSQL feature, not an external library or middleware. It applies to all connections to the database — direct psql sessions, admin tools, and application connections alike.
- **Audit tool**: during a security audit, demonstrating RLS policies is strong evidence that tenant isolation is enforced at the database layer, independently of application code.

### Negative

- **Performance overhead**: every query plan must include the RLS predicate. For simple queries this adds negligible overhead; for complex queries with many joins, the planner must incorporate the tenant filter across all joined tables. Proper indexing on `tenant_id` columns (or composite indexes like `(tenant_id, id)`) is essential.
- **`SET LOCAL` must not be forgotten**: if a transaction runs without `SET LOCAL pravah.current_tenant_id`, queries return zero rows. This is the safe failure mode, but it manifests as unexpected empty results that require debugging. Integration tests that run without tenant context catch this early.
- **Migration complexity**: running schema migrations requires `pravah_admin` (which bypasses RLS) or temporarily setting a tenant context. Flyway and Liquibase migrations run as `pravah_admin` to avoid RLS restrictions on DDL operations.
- **PgBouncer compatibility**: `SET LOCAL` is transaction-scoped and works correctly with PgBouncer in transaction mode. Session-scoped `SET` commands (`SET pravah.current_tenant_id`) would not — PgBouncer resets session state between transactions, but `SET` (not `SET LOCAL`) would persist within a session and potentially leak across tenants sharing a connection in session mode.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| PgBouncer session mode accidentally used | PgBouncer configured strictly in transaction mode; CI test validates that `SET LOCAL` variables are cleared between transactions |
| Flyway migration accidentally runs as `pravah_app` and is blocked by RLS | Migrations use a dedicated `pravah_admin` connection string configured in the migration tool; `pravah_app` credentials are not in the migration configuration |
| Performance degradation from RLS predicates | All tenant-scoped tables have composite indexes on `(tenant_id, <query_column>)`; `EXPLAIN ANALYZE` confirms index use during query planning review |
| Policy definition error allows too-permissive access | RLS policies are tested with a dedicated test suite that connects as `pravah_app`, sets different `current_tenant_id` values, and asserts that cross-tenant rows are never returned |

---

## Alternatives Considered

### Application-Only Tenant Filtering

Rely solely on `findByIdAndTenantId()` repository methods and application-level checks.

Rejected as the sole mechanism because:
- Software has bugs. A single missing tenant filter is a critical security incident.
- RLS provides a mathematically verifiable guarantee that is independent of application code quality.
- SOC 2 Type II and similar audits look for controls that operate independently of application code.

Application-layer filtering is still used as the primary layer — it provides better error messages and is more performant than relying on RLS for all filtering. RLS is the safety net.

### Schema-per-Tenant

Each tenant's data lives in a separate PostgreSQL schema. RLS is not needed because schemas are inherently separated.

Rejected (see ADR-006). Schema-per-tenant does not scale operationally to thousands of tenants and has PgBouncer compatibility issues.

### Column-Level Encryption per Tenant

Encrypt all sensitive columns with a per-tenant key so that even a cross-tenant data leak returns encrypted ciphertext.

Used as a complementary control (Vault Transit engine per-tenant keys, ADR-007) for particularly sensitive fields (pipeline secrets, OAuth tokens, credentials). Not a replacement for RLS because:
- Encryption protects data confidentiality but not data access control. A query that bypasses tenant isolation would still return ciphertext — which does not constitute isolation.
- Decrypting ciphertext requires calling Vault with the tenant's key. If the application code has a bug that fetches cross-tenant rows, it would use the wrong key for decryption — but this is not a reliable security control.

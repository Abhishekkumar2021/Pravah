# Playground 03 — PostgreSQL advanced (partitioning, RLS, PgBouncer)

Hands-on exercise for:

- **[ADR-003: PostgreSQL database-per-service](../../docs/adr/ADR-003-postgresql-database-per-service.md)** — one logical schema here; same SQL patterns Pravah uses per service DB.
- **[ADR-013: Row-Level Security](../../docs/adr/ADR-013-postgresql-rls-tenant-isolation.md)** — `pravah.current_tenant_id` + `SET LOCAL` per transaction.
- **[ADR-022: Patroni HA](../../docs/adr/ADR-022-patroni-postgresql-ha.md)** — read-only context: production failover is not simulated in Docker; you learn single-node Postgres first.

---

## What is already built

| Piece | Purpose |
|-------|---------|
| `docker-compose.yml` | **PostgreSQL 16** (host **5432**) + **PgBouncer** (host **6432**) — both start with `docker compose up -d` |
| `application.yml` | App JDBC → **6432** (pooler); Flyway → **5432** (Postgres direct) |
| `db/migration/V1__*.sql` | Role `app_tenant`, **partitioned** `audit_events` + `DEFAULT` partition |
| `db/migration/V2__*.sql` | `playground_jobs` with **RLS** + `FORCE ROW LEVEL SECURITY` |
| `TenantScopedJdbc` | Example `SET LOCAL pravah.current_tenant_id` then DML (ADR-013) |
| `RlsAndPartitioningIT` | Testcontainers integration tests (**requires Docker** when not skipped) |

Flyway runs as **`playground`** (superuser from Docker); the app runtime user is **`app_tenant`** so RLS is enforced (superuser would bypass RLS).

---

## Prerequisites

- Java 21, `./gradlew`
- **Docker** (for `docker compose` and for `./gradlew test` integration tests)
- Optional: `psql` for ad-hoc SQL

---

## Quick start

```bash
cd playground/03-postgres-advanced
docker compose up -d    # PostgreSQL + PgBouncer (both required)
./gradlew bootRun
```

**Ports**

| Client | Host port | User (example) | Use case |
|--------|-----------|----------------|----------|
| Spring app (`spring.datasource`) | **6432** | `app_tenant` | Normal JDBC; goes through **PgBouncer** |
| Flyway (`spring.flyway`) | **5432** | `playground` | Migrations; **must not** use the pooler |
| `psql` / admin | **5432** | `playground` | `docker compose exec postgres psql …` |

Database: **`playground`**. Passwords: **`playground`** for both roles in this demo.

---

## Tasks (recommended order)

### Task 1 — Read the migrations

1. Open `V1__roles_and_partitioned_audit.sql`. Explain why `SET ROLE app_tenant` is used before `CREATE TABLE`.
2. Open `V2__playground_jobs_rls.sql`. Relate `USING` / `WITH CHECK` to SELECT vs INSERT.

### Task 2 — Prove RLS in `psql`

```bash
docker compose exec postgres psql -U playground -d playground
```

```sql
SET ROLE app_tenant;
SET LOCAL pravah.current_tenant_id = 'acme';
INSERT INTO playground_jobs (tenant_id, name) VALUES ('acme', 'from-psql');
SELECT * FROM playground_jobs;
RESET ROLE;
```

Try inserting `tenant_id = 'evil'` while `pravah.current_tenant_id` is `acme` — expect **failure** (WITH CHECK).

### Task 3 — Partition pruning

Insert rows into `audit_events` with `created_at` inside **May 2026** vs **today**. Query `audit_events_2026_05` vs `audit_events_default` and see where rows landed (`EXPLAIN` optional).

### Task 4 — PgBouncer transaction mode

The app already uses **6432** (PgBouncer) for every JDBC call. Confirm that short transactions still see the correct tenant after `SET LOCAL pravah.current_tenant_id` (ADR-013: safe with **transaction** pooling). Optionally trace with `docker compose logs pgbouncer`.

### Task 5 — Patroni (theory)

Read ADR-022: leader/replica, synchronous replication, failover. **No lab in this compose file** — note how Patroni would sit *outside* this single-node demo.

---

## Tests

```bash
./gradlew test
```

- If **Docker is unavailable**, Testcontainers marks the integration class **skipped** (`disabledWithoutDocker = true`) so CI/sandbox can still compile.
- With Docker, `RlsAndPartitioningIT` runs Flyway on a throwaway Postgres container.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| **`failed to connect to the docker API` … `/var/run/docker.sock`** | The Docker **daemon is not running**, or your CLI is using the **wrong socket**. Start **Docker Desktop**, **OrbStack**, **Colima**, or **Rancher Desktop**, then retry. On **OrbStack** (macOS), you may need: `export DOCKER_HOST=unix://${HOME}/.orbstack/run/docker.sock` (or pick the OrbStack context in Docker Desktop settings). After installing Docker the first time, **log out and back in** or open the app once so it creates the socket. |
| Flyway auth failed | Postgres must be up first; Flyway uses `playground` user on port **5432** |
| Flyway “PostgreSQL 16 … newer than this version” | This project pins **Flyway 10.22+** in `build.gradle.kts` so PG 16 is supported. Run `./gradlew clean` if you still see an old warning from a cached classpath. |
| **`manifest unknown`** for `edoburu/pgbouncer` | Use a published tag (this repo uses `v1.25.1-p0`). Bare versions like `v1.23.1` are not valid on Docker Hub — tags include a suffix (e.g. `v1.23.1-p3`). |
| PgBouncer auth failed | Ensure `docker compose up -d` finished (Postgres **healthy** before PgBouncer starts); same password as Postgres |
| **`Connection refused` on 6432** | PgBouncer container not running — run `docker compose up -d` and wait for both services |
| RLS returns no rows | Forgot `SET LOCAL pravah.current_tenant_id` in the same transaction |

---

## Next

**[04-redis](../04-redis/)** — distributed locks and rate limiting (ADR-012).

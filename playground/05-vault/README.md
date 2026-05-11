# Playground 05 — HashiCorp Vault (dynamic DB creds + PKI)

**Pravah ADRs**

- [ADR-007: Vault for secret management](../../docs/adr/ADR-007-vault-secret-management.md) — Database secrets engine, leases, audit trail.
- [ADR-008: mTLS](../../docs/adr/ADR-008-mtls-service-to-service.md) — Certificates come from a CA; locally you issue leaf certs via Vault PKI (production pairs this with cert-manager / mesh patterns).

This playground shows two engines:

1. **Database** — Vault creates short-lived PostgreSQL roles; your app never stores a long-lived DB password.
2. **PKI** — Vault acts as an internal CA; you request a leaf cert + key for a SAN/CN (runner / service identity in the full platform).

Production uses **Kubernetes auth + Vault Agent sidecars** instead of a root token — here you use **token auth + root** only for clarity.

---

## Prerequisites

- Java 21, Docker, Docker Compose
- `./gradlew` from this directory (Gradle 8.12.1 wrapper)

---

## Quick start (manual exploration)

From the **Pravah** repo root (adjust path if yours differs):

```bash
cd playground/05-vault
docker compose up -d
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=root   # dev token only — never in production
chmod +x vault/scripts/init-vault.sh   # once
./vault/scripts/init-vault.sh
```

If `cd playground/05-vault` fails, you are not in the repo root — use `cd /path/to/Pravah/playground/05-vault`.

The init script **waits with `curl`** and then uses your local `vault` CLI if installed. If not, it automatically falls back to the CLI inside the running Docker container, so no host install is required for this playground.

Then try:

```bash
docker compose exec -T -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root \
  vault vault read database/creds/playground

docker compose exec -T -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root \
  vault vault write pki/issue/playground common_name=svc.playground.local ttl=1h
```

If you install the [Vault CLI](https://developer.hashicorp.com/vault/downloads), the same commands become shorter: `vault read ...` and `vault write ...`.

---

## Automated verification

```bash
./gradlew test
```

`VaultPlaygroundIT` starts **PostgreSQL + Vault** on a shared Docker network (Vault reaches Postgres at hostname `pg`), bootstraps the Database and PKI engines via HTTP, then:

- leases PostgreSQL credentials and opens a **JDBC** connection with the leased user/password;
- issues a **PKI** leaf certificate and asserts PEM material is present.

If Docker is not running, Testcontainers marks these tests **skipped** (same pattern as playground 03).

---

## Code map

| Piece | Role |
|------|------|
| `VaultClientConfiguration` | `VaultTemplate` + token auth → call Vault’s HTTP API through Spring Vault |
| `DynamicDatabaseCredentials` | `read database/creds/playground` → username/password + lease duration |
| `PkiIssueService` | `write pki/issue/playground` → certificate + private key PEM |
| `PlaygroundVaultHttpBootstrap` (tests) | Enables mounts and roles — mirrors `./vault/scripts/init-vault.sh` |

---

## Tasks (your turn)

1. **Lease renewal** — Note `lease_duration` on `database/creds` responses. Read Vault docs for `sys/leases/renew` and sketch how a connection pool would refresh creds before expiry.
2. **Policies** — Root token bypasses all policies. In a real cluster you’d create a policy allowing only `database/creds/playground` and `pki/issue/playground`. Outline what that HCL policy would contain.
3. **Kubernetes auth** — ADR-007 describes `auth/kubernetes/login`. Summarize how the Vault Agent sidecar avoids embedding Vault tokens in your app image.
4. **mTLS hook** — ADR-008: issued cert + key would feed Netty/gRPC `SslContext`. You don’t need to implement TLS here — just save the PEMs to files and point your **02-grpc** playground at them when you’re ready.

---

## Troubleshooting

| Symptom | Fix |
|--------|-----|
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Another Postgres uses 5432 (e.g. playground 03). This compose file maps Postgres to **host port 5433** (`5433:5432`). Run `docker compose up -d` again; connect from the host with `localhost:5433` if you need `psql`. |
| `connection refused` to `:8200` | `docker compose up -d`; wait a few seconds |
| Database engine errors in init script | Ensure the **postgres** container is running (`docker compose ps`). Vault talks to `postgres:5432` **inside** the Docker network — independent of the host port |
| Tests skipped | Start Docker Desktop / daemon |
| `docker logs` shows `flag provided but not defined: -dev-root-token` | Fixed in current `docker-compose.yml`: Vault **1.15+** expects **`VAULT_DEV_ROOT_TOKEN_ID`** (env), not `-dev-root-token`. Run `docker compose down && docker compose up -d` to recreate the container. |

---

## Next

**06-outbox-pattern** — atomic Kafka publish after PostgreSQL commit (ADR-004).

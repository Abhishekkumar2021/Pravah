# ADR-007: HashiCorp Vault for Secret Management

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah services need access to secrets at runtime:

- PostgreSQL credentials (username, password, connection string)
- Kafka SASL credentials
- Signing keys for JWTs
- API keys for third-party services (notification providers, webhook endpoints)
- Per-tenant encryption keys for sensitive pipeline data
- TLS certificates for internal mTLS communication

The question is how these secrets are stored, distributed to services at runtime, rotated, and audited.

**Why Kubernetes Secrets are not sufficient on their own:**

Kubernetes Secrets are base64-encoded, not encrypted. They are stored in etcd, which by default does not encrypt at rest (encryption at rest requires explicit configuration and is often missed). More critically:

- Secrets are **static**: a PostgreSQL password stored in a Kubernetes Secret does not expire. If the credential leaks (e.g., in a log line, a debug dump, a git commit), it remains valid until manually rotated — and manual rotation is often delayed because it requires coordinating a database password change with a Kubernetes Secret update with a pod restart.
- **No audit trail**: accessing a Kubernetes Secret does not generate an audit event. There is no way to know which pod read which secret, or when.
- **No dynamic secrets**: Kubernetes Secrets cannot generate a new PostgreSQL password for each pod startup with a limited lifetime.
- **No fine-grained policies**: a service account that can read one Kubernetes Secret in a namespace can often read all Secrets in that namespace, depending on RBAC configuration.

For a multi-tenant platform where tenant credentials are stored (e.g., the database credentials Pravah uses to connect to a tenant's data warehouse), the stakes are higher. A compromised credential must be immediately revocable, not just rotatable.

---

## Decision

HashiCorp Vault is the secrets management system for all Pravah services.

**Authentication to Vault:**

Services running in Kubernetes authenticate to Vault using the **Kubernetes Auth Method**. Each service's Kubernetes ServiceAccount has a corresponding Vault role that specifies which secrets it can access.

```
Pod starts
  │
  ├── Kubernetes mounts ServiceAccount token at
  │   /var/run/secrets/kubernetes.io/serviceaccount/token
  │
  └── Vault Agent sidecar reads the ServiceAccount token
      and calls vault.auth.kubernetes.login()
          │
          Vault verifies the token with the Kubernetes API
          Vault issues a short-lived Vault token
          │
          Vault Agent caches the token and renews it automatically
          Vault Agent renders secrets into /vault/secrets/ (tmpfs)
```

**Secret engines in use:**

| Engine | Purpose | Example |
|--------|---------|---------|
| KV v2 | Static secrets with versioning | JWT signing keys, API keys for external services |
| Database | Dynamic PostgreSQL credentials | Per-service DB credentials, 1-hour TTL |
| PKI | Certificate authority for internal mTLS | Short-lived TLS certificates for services |
| Transit | Encryption-as-a-service | Per-tenant encryption keys — keys never leave Vault |

**Dynamic database credentials:**

This is the highest-value feature for Pravah. Instead of a static password in a config file:

```
Vault Database engine generates:
  username: v-k8s-exec-svc-AbCdEf  (unique per lease)
  password: A7xK2p...              (random, 32 chars)
  TTL: 1 hour

PostgreSQL: CREATE ROLE v-k8s-exec-svc-AbCdEf WITH LOGIN PASSWORD '...'
             VALID UNTIL NOW() + INTERVAL '1 hour';

After 1 hour: Vault revokes the lease.
PostgreSQL: DROP ROLE v-k8s-exec-svc-AbCdEf;
```

If this credential leaks, it is worthless within the hour. There is no long-lived static password to protect.

**Vault Agent sidecar pattern:**

Vault Agent runs as a sidecar container. It handles all Vault communication so the application service does not need a Vault SDK or Vault knowledge:

```yaml
initContainers:
  - name: vault-agent-init
    image: hashicorp/vault:1.15
    args: ["agent", "-config=/vault/config/init.hcl"]
    # Renders secrets before the main container starts

containers:
  - name: execution-service
    image: pravah/execution-service:latest
    # Reads secrets from /vault/secrets/ — plain files

  - name: vault-agent
    image: hashicorp/vault:1.15
    args: ["agent", "-config=/vault/config/agent.hcl"]
    # Keeps secrets fresh; re-renders on renewal
```

Secrets are rendered as files on a `tmpfs` volume (never written to disk). The application reads them as environment variables or configuration files. When a secret is renewed, the file is updated and the application re-reads it (Spring Cloud Vault, or a custom `@RefreshScope` bean).

**Per-tenant encryption with Transit engine:**

Sensitive pipeline data (e.g., OAuth tokens, database passwords stored as pipeline inputs) are encrypted at the application layer using Vault's Transit engine. Each tenant has a dedicated Transit encryption key:

```
Encryption:   vault.transit.encrypt("tenant-megacorp", plaintext) → ciphertext
Decryption:   vault.transit.decrypt("tenant-megacorp", ciphertext) → plaintext
```

The encryption keys never leave Vault. The service never holds a raw key — only ciphertext. If Tenant A's key is compromised, Tenant B's data remains protected.

---

## Consequences

### Positive

- **Short-lived credentials reduce blast radius**: a leaked dynamic database credential expires within the hour. The time window for exploitation is small.
- **Centralized audit**: every secret read, write, or token issue generates an audit log entry. Vault's audit log answers: which service read which secret, when, and from which Kubernetes namespace.
- **Zero standing secrets**: services do not start with a pre-configured password. Credentials are fetched at runtime and expire. There is no long-lived static secret to protect across deployments, backups, or git history.
- **Key rotation without downtime**: Vault's KV v2 versioning allows rotating a JWT signing key by writing a new version. Services that have already issued tokens continue to validate against the old key (still readable via version number) while new tokens use the new key. Rotation is coordinated without downtime.
- **Transit engine eliminates key management code**: the application does not implement encryption algorithms, key storage, or IV management. Vault's Transit engine is a FIPS 140-2 validated encryption service.

### Negative

- **Vault is a critical dependency**: if Vault is unavailable, services cannot start (cannot fetch initial credentials). Vault must be a highly-available, well-operated component.
- **Operational complexity**: Vault requires initialization, unsealing, and HA configuration (Raft consensus cluster or cloud HA). Auto-unseal via cloud KMS (AWS KMS, GCP Cloud KMS) eliminates the manual unseal step after restart.
- **Agent sidecar adds resource overhead**: every pod runs a Vault Agent sidecar. At 100 pods, this is 100 additional containers consuming CPU and memory.
- **Secret renewal edge cases**: if the Vault Agent fails to renew a lease before expiry, the application's database credential becomes invalid mid-operation. This requires the connection pool to detect and handle `authentication failed` errors gracefully.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Vault cluster unavailability | 3-node Raft HA cluster; auto-unseal via cloud KMS; Vault agent caches last-known secrets for graceful degradation during short outages |
| Vault Agent fails to renew credential | Connection pool monitors authentication errors and triggers a re-fetch; Vault agent alert on renewal failure |
| Vault audit log grows unboundedly | Audit log shipped to Elasticsearch; Vault's local audit log rotated and compressed; retention policy: 90 days |
| Developer accesses Vault in production | Vault policies grant only machine identities (Kubernetes ServiceAccounts) in production; human access to production Vault requires break-glass procedure with full audit |

---

## Alternatives Considered

### Kubernetes Secrets with Encryption at Rest

Enable etcd encryption at rest + Sealed Secrets or External Secrets Operator to sync from a cloud KMS.

Considered as a simpler alternative. Rejected because:
- Static secrets remain static — no TTL, no automatic rotation, no per-access audit.
- Kubernetes Secrets are cluster-scoped. Managing secrets across multiple clusters (for multi-region deployments) requires additional tooling.
- No equivalent to the Transit engine for per-tenant encryption without implementing it in application code.
- Vault is the industry standard for this use case and worth the operational investment.

### AWS Secrets Manager / GCP Secret Manager

Managed cloud services that provide secret storage with rotation, versioning, and audit.

Rejected for the same reason as cloud-managed messaging: Pravah must be deployable on-premise and in private clouds. A hard dependency on a specific cloud's secrets manager violates the multi-cloud deployment requirement. Vault can be deployed anywhere: on Kubernetes, on bare metal, in a VM.

### Environment Variables from CI/CD

Store secrets as environment variables in Kubernetes Deployments, populated by the CI/CD system (GitHub Actions secrets → Kubernetes Secret → env var).

Rejected because:
- Environment variables are visible in the pod spec, in `kubectl describe pod` output, and often in log output. They are the least secure way to handle secrets.
- CI/CD system becomes the secrets distribution mechanism; secrets must be updated in CI/CD whenever they change.
- No audit trail, no TTL, no rotation mechanism.

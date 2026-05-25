# Pravah Deployment (Helm)

> **Full deployment guide (local, free cloud, AWS):** [docs/deployment/DEPLOYMENT_GUIDE.md](../docs/deployment/DEPLOYMENT_GUIDE.md)

Production-grade Kubernetes packaging for the Pravah control plane per [ADR-010](../docs/adr/ADR-010-kubernetes-helm-argocd.md).

## Chart Features

- **Secret management** - Credentials via Kubernetes Secrets (supports external secrets operators)
- **Security contexts** - Non-root containers, read-only filesystem, dropped capabilities
- **Network policies** - Service isolation when enabled
- **Health probes** - Startup, readiness, and liveness probes tuned for JVM services
- **Horizontal Pod Autoscaler** - CPU/memory based scaling
- **Pod Disruption Budget** - Graceful rollouts
- **Pod anti-affinity** - Spread replicas across nodes
- **Topology spread constraints** - Multi-zone distribution
- **ServiceMonitor** - Prometheus integration (kube-prometheus-stack)

## Layout

```
deploy/helm/pravah-platform/
  Chart.yaml
  values.yaml           # Base defaults (single-node friendly)
  values-local.yaml     # kind / minikube (local images)
  values-prod.yaml      # Production (external DB/Kafka/Redis, HA, secrets)
  templates/
    _helpers.tpl        # Template helpers
    secrets.yaml        # Kubernetes Secret for credentials
    postgres.yaml       # Bundled PostgreSQL StatefulSet
    pgbouncer.yaml      # PgBouncer Deployment (transaction pooling, US-09.16)
    configmap-pgbouncer.yaml
    kafka.yaml          # Bundled Kafka (KRaft) StatefulSet
    redis.yaml          # Bundled Redis StatefulSet
    services.yaml       # Pravah service Deployments
    gateway.yaml        # API Gateway Deployment
    configmap-*.yaml    # Service configurations
    ingress.yaml        # Ingress resource
    networkpolicy.yaml  # Network policies
    pdb.yaml            # Pod Disruption Budgets
    hpa.yaml            # Horizontal Pod Autoscalers
    servicemonitor.yaml # Prometheus ServiceMonitors
```

**Platform services:** `gateway`, `tenant-service`, `pipeline-service`, `execution-service`, `scheduler-service`, `notification-service`, `connect-service`, `runner-service` (HTTP + gRPC 9091).

**Bundled dependencies (disable in production):** PostgreSQL 16, Apache Kafka 3.7 (KRaft), Redis 7, MinIO (artifacts), optional Mailhog (local SMTP), optional Vault dev server (local KV secrets).

**Production secrets (Vault):** Set `externalVault.address` and `services.pipeline-service.needsVault: true` with `vaultKubernetesRole: pravah-pipeline-service`. Pipeline resolves `vault:path#key` references via token auth (bundled dev) or Kubernetes auth (production). For stored tenant secrets (`transit` provider), enable the **Transit** secrets engine on your Vault cluster (mount `transit/` by default); per-tenant keys are created on first write.

Example Vault policy for pipeline-service: [`deploy/argocd/vault-policies/pravah-pipeline-service.hcl`](argocd/vault-policies/pravah-pipeline-service.hcl) (KV read + Transit encrypt/decrypt on `tenant-*` keys).

**Vault Transit (tenant secrets, pathway #8):** When `vault.enabled=true` and `vaultTransit.enabled=true` (default in `values-local.yaml` with `pipeline-service.needsVault`), a post-install Helm job enables the Transit engine. Manual bootstrap: `./scripts/deploy/k8s-local-vault-transit.sh pravah`. Docker Compose / bare metal: `./backend/scripts/vault/init-local-transit.sh`.

**Runner gRPC mTLS (ADR-005/008):** When `services.runner-service.exposeGrpc.enabled` is true, set `runnerGrpcTls.enabled=true` and provide `runnerGrpcTls.existingSecret` with keys `tls.crt`, `tls.key`, and `ca.crt` (client CA for mTLS). Production values enable this by default. Local kind/minikube:

```bash
# Optional: embed registered runner identity in client cert SAN (SPIFFE URI)
RUNNER_ID=<uuid> TENANT_ID=<uuid> ./scripts/deploy/generate-runner-grpc-tls.sh
./scripts/deploy/k8s-local-runner-grpc-tls.sh pravah
helm upgrade --install pravah deploy/helm/pravah-platform ... \
  --set runnerGrpcTls.enabled=true \
  --set runnerGrpcTls.existingSecret=pravah-runner-grpc-tls
```

When mTLS is enabled, `RunnerGrpcIdentityInterceptor` extracts `runner_id` (and optional `tenant_id`) from the client certificate SPIFFE URI — not from the heartbeat payload. Heartbeats with a mismatched `runner_id` are rejected.

**Local kind/minikube (full mTLS + Vault PKI path):**

```bash
./scripts/deploy/generate-runner-grpc-tls.sh
./scripts/deploy/k8s-local-runner-grpc-tls.sh pravah
./scripts/deploy/k8s-local-vault-pki.sh pravah   # replaces ca.crt with Vault PKI CA
helm upgrade --install pravah deploy/helm/pravah-platform ... \
  --set runnerGrpcTls.enabled=true \
  --set runnerGrpcTls.existingSecret=pravah-runner-grpc-tls \
  --set runnerPki.enabled=true \
  --set services.runner-service.needsVault=true
./scripts/deploy/k8s-local-smoke.sh pravah
```

Runner agent registers once with `--tls-enabled --tls-trust-cert=ca.crt` (server trust only); Vault PKI returns client cert in `RegisterRunnerResponse.mtls` and the agent applies it before opening the Connect stream.

**cert-manager (runner gRPC server TLS):** Set `certManager.runnerGrpc.enabled=true` and `issuerName` to auto-renew the server certificate Secret (instead of manual `generate-runner-grpc-tls.sh`).

Runner agent (outside cluster): `--tls-enabled --tls-trust-cert=ca.crt --tls-client-cert=client.crt --tls-client-key=client.key` (files from `deploy/certs/runner-grpc/`). When Vault PKI is enabled, the agent receives client cert material in the registration response and persists it automatically.

## Prerequisites

- Kubernetes 1.27+ (kind, minikube, EKS, GKE, AKS)
- [Helm](https://helm.sh/) 3.12+
- `kubectl` configured for target cluster
- For local images: Docker, JDK 21

## Local Kubernetes (kind / minikube)

### 1. Create Cluster

```bash
# kind
kind create cluster --name pravah

# minikube
minikube start --cpus=4 --memory=8g
```

### 2. Build and Load Images

```bash
./scripts/deploy/k8s-local-build.sh
```

Builds `pravah-<service>:local` images and loads them into the cluster.

### 3. Install Chart

```bash
./scripts/deploy/k8s-local-install.sh
```

Or manually:

```bash
helm upgrade --install pravah deploy/helm/pravah-platform \
  --namespace pravah --create-namespace \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml
```

### 4. Verify platform health

```bash
./scripts/deploy/k8s-local-smoke.sh pravah
```

Waits for all 8 platform pods (plus bundled Postgres/Kafka/Redis/MinIO/Mailhog/Vault when enabled), then runs in-cluster HTTP checks against each service `/actuator/health` (expects `"status":"UP"`), tenant JWKS, and Vault when deployed.

Optional env: `HELM_RELEASE` (default `pravah`), `HELM_FULLNAME` (auto from `helm get values` when `jq` is installed), `SMOKE_TIMEOUT` (default `600s`).

Alternatively:

```bash
kubectl -n pravah wait --for=condition=ready pod --all --timeout=600s
```

### 3b. GitOps with Argo CD (pathway #9, ADR-010)

Install Argo CD and let it reconcile `deploy/helm/pravah-platform` from Git instead of direct `helm upgrade`:

```bash
./scripts/deploy/k8s-local-build.sh          # load :local images into kind first
./scripts/deploy/k8s-local-argocd.sh pravah  # installs Argo CD + Application
./scripts/deploy/k8s-local-smoke.sh pravah
```

Manifests live in `deploy/argocd/`:

| File | Purpose |
|------|---------|
| `appproject-pravah.yaml` | AppProject RBAC (namespace `pravah`, Git repo allowlist) |
| `applications/pravah-platform-local.yaml` | Auto-sync from `develop` with `values-local.yaml` |
| `applications/pravah-platform-ci.yaml` | CI/kind smoke: `values-local.yaml` + `values-ci.yaml` (no ingress, extended probes) |
| `applications/pravah-platform-production.yaml` | Production Application (manual sync, `values-prod.yaml`) |
| `applications/pravah-platform-production.yaml.example` | Annotated template for fork-specific edits |
| `vault-policies/pravah-pipeline-service.hcl` | External Vault policy example (KV + Transit) |

**Important:** Argo CD reads the chart from **Git** (`develop` branch), not your working tree. Push chart changes before expecting Argo CD to apply them.

Production: apply `applications/pravah-platform-production.yaml` (manual sync). The **Deploy · GitOps image tag** job on `main` commits the pinned `image.tag` to `values-prod.yaml`; Argo CD picks it up on sync. Rollback = Git revert or `argocd app rollback`.

**Gateway:** Tenant secrets API (`/api/v1/secrets/**`) routes through the API gateway to pipeline-service (required for stored Transit secrets via port 8080).

Validate manifests locally: `make validate-argocd`

### CI / nightly kind smoke

GitHub Actions workflow **K8s · kind smoke** (`.github/workflows/k8s-smoke-nightly.yml`) runs daily at 03:00 UTC and on PRs that touch `deploy/`, `scripts/deploy/`, or `backend/`:

| Job | What it validates |
|-----|-------------------|
| **K8s · kind smoke** | Direct Helm install (`values-local.yaml` + `values-ci.yaml`) + platform smoke |
| **K8s · Argo CD smoke** | Argo CD install + GitOps sync from PR branch + platform smoke |

Both jobs: ephemeral `kind` cluster (`deploy/kind/pravah-ci.yaml`), build/load 8 service images, then `k8s-local-smoke.sh` (actuator health + JWKS + Vault).

Local reproduction (requires Docker, kind, JDK 21, Helm):

```bash
make k8s-ci-smoke
make k8s-ci-smoke-argocd
# Debug: SKIP_CLUSTER_DELETE=1 make k8s-ci-smoke
```

On failure, logs are written to `build/k8s-ci-smoke/` or `build/k8s-ci-smoke-argocd/` and uploaded as CI artifacts.

## Observability (pathway #11)

Install Prometheus, Grafana, and Jaeger; enable ServiceMonitors and OTLP tracing:

```bash
./scripts/deploy/k8s-local-observability.sh
helm upgrade --install pravah deploy/helm/pravah-platform \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml \
  -f deploy/helm/pravah-platform/values-observability.yaml \
  --namespace pravah --create-namespace
```

Validate: `make validate-observability`. Detail: [deploy/observability/README.md](observability/README.md).

### 5. Seed Demo Data

```bash
./scripts/deploy/k8s-local-seed.sh pravah
```

### 6. Access API

```bash
kubectl -n pravah port-forward svc/pravah-gateway 8080:8080
```

In another terminal:

```bash
cd web && VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

Sign in: `dev@localhost.pravah` / `PravahDev1!`

## Cloud infrastructure (Terraform, pathway #10)

Provision AWS managed services (VPC, EKS, RDS, ElastiCache, S3, optional MSK) before Helm/Argo CD:

```bash
cd deploy/terraform/aws/reference
cp terraform.tfvars.example terraform.tfvars   # set unique artifacts_bucket_name
terraform init && terraform apply
aws eks update-kubeconfig --name "$(terraform output -raw eks_cluster_name)"
../../../../scripts/deploy/terraform-init-rds.sh
```

See [deploy/terraform/README.md](terraform/README.md) for Helm value mapping, cost estimates, operator scripts, and CI validation (`make validate-terraform`).

## Production Deployment

Use GHCR images from CI (`ghcr.io/abhishekkumar2021/pravah-*`) and external managed services (RDS, MSK, ElastiCache):

### 1. Create Secrets

```bash
kubectl create namespace pravah

# PostgreSQL credentials
kubectl -n pravah create secret generic pravah-postgres-credentials \
  --from-literal=username=pravah \
  --from-literal=password='<secure-password>'

# Pravah application secret (internal S2S + optional dedicated runner bootstrap key)
kubectl -n pravah create secret generic pravah-credentials \
  --from-literal=postgres-username=pravah \
  --from-literal=postgres-password='<secure-password>' \
  --from-literal=internal-service-secret='<32-char-secret>' \
  --from-literal=runner-bootstrap-secret='<32-char-runner-secret>'

# S3 / artifact credentials (when minio.enabled=false)
kubectl -n pravah create secret generic pravah-artifact-credentials \
  --from-literal=access-key='<s3-access-key>' \
  --from-literal=secret-key='<s3-secret-key>'

# SMTP credentials (notification-service)
kubectl -n pravah create secret generic pravah-smtp-credentials \
  --from-literal=password='<smtp-password>'

# Runner gRPC TLS credentials (production — create before helm install)
kubectl -n pravah create secret generic pravah-runner-grpc-tls \
  --from-file=tls.crt=server.pem \
  --from-file=tls.key=server-key.pem \
  --from-file=ca.crt=client-ca.pem

# Image pull secret (for private registry - only if repo is private)
kubectl -n pravah create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=Abhishekkumar2021 \
  --docker-password=<github-pat>
```

### 2. Install with Production Values

```bash
helm upgrade --install pravah deploy/helm/pravah-platform \
  --namespace pravah --create-namespace \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-prod.yaml \
  --set image.tag=<git-sha> \
  --set externalPostgres.host=<rds-endpoint> \
  --set externalKafka.bootstrapServers=<msk-endpoint> \
  --set externalRedis.host=<elasticache-endpoint> \
  --set externalArtifact.endpoint=https://s3.<region>.amazonaws.com \
  --set smtp.host=smtp.example.com \
  --set smtp.port=587 \
  --set smtp.existingSecret=pravah-smtp-credentials \
  --set pravah.wsAllowedOrigins=https://app.pravah.io \
  --set pravah.frontendBaseUrl=https://app.pravah.io \
  --set pravah.hooksBaseUrl=https://api.pravah.io/api/v1/hooks \
  --set ingress.host=api.pravah.io
```

**PgBouncer (US-09.16):** `values-prod.yaml` sets `pgbouncer.enabled: true`. Image: `edoburu/pgbouncer` with a `-pN` patch tag (bare `1.23.1` is not published). Services connect to `<release>-pgbouncer:6432` with `prepareThreshold=0` (required for transaction pooling). PgBouncer pools to RDS on port 5432. Metrics: `pgbouncer-exporter` on port 9127 + ServiceMonitor when `metrics.serviceMonitor.enabled`. Alerts: `deploy/observability/prometheus-rules/pravah-platform.yaml` (`PravahPgBouncerClientWaiting`, `PravahPgBouncerPoolSaturated`, `PravahPgBouncerMaxWait`). Validate: `./scripts/deploy/validate-pgbouncer.sh`.

Local kind/minikube enables PgBouncer via `values-local.yaml` (bundled Postgres backend).

### 3. Verify Deployment

```bash
kubectl -n pravah get pods
kubectl -n pravah get svc
kubectl -n pravah describe ingress pravah
```

## Configuration Reference

### Global

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.registry` | Container registry | `ghcr.io` |
| `image.repositoryPrefix` | Image name prefix | `abhishekkumar2021/pravah` |
| `image.tag` | Image tag | `latest` |
| `image.pullPolicy` | Pull policy | `IfNotPresent` |
| `image.pullSecrets` | Image pull secrets | `[]` |

### Bundled Dependencies

| Parameter | Description | Default |
|-----------|-------------|---------|
| `postgres.enabled` | Deploy bundled PostgreSQL | `true` |
| `pgbouncer.enabled` | Route JDBC via PgBouncer (transaction pool) | `false` (`true` in values-local/prod) |
| `pgbouncer.poolMode` | PgBouncer pool mode | `transaction` |
| `pgbouncer.defaultPoolSize` | Server connections per database/user | `25` |
| `kafka.enabled` | Deploy bundled Kafka | `true` |
| `redis.enabled` | Deploy bundled Redis | `true` |

### External Dependencies

| Parameter | Description | Default |
|-----------|-------------|---------|
| `externalPostgres.host` | PostgreSQL host (when `postgres.enabled=false`) | `""` |
| `externalPostgres.existingSecret` | Existing secret for credentials | `""` |
| `externalKafka.bootstrapServers` | Kafka bootstrap servers | `""` |
| `externalRedis.host` | Redis host | `""` |
| `minio.enabled` | Deploy bundled MinIO for artifacts | `true` |
| `externalArtifact.endpoint` | S3 endpoint (required when `minio.enabled=false`) | `""` |
| `externalArtifact.existingSecret` | Secret with artifact access/secret keys | `""` |
| `mailhog.enabled` | Bundled Mailhog SMTP (local dev) | `false` |
| `smtp.host` | External SMTP host (production) | `""` |
| `smtp.existingSecret` | SMTP password secret | `""` |

### Application

| Parameter | Description | Default |
|-----------|-------------|---------|
| `pravah.jwtIssuer` | JWT issuer | `pravah-dev` |
| `pravah.internalServiceSecret` | Inter-service auth secret (chart Secret) | `pravah-local-internal-secret` |
| `pravah.internalServiceExistingSecret` | Use pre-created Secret instead of chart | `""` (prod: `pravah-credentials`) |
| `requireProductionSecrets` | Fail template if prod secrets missing | `false` (`true` in values-prod) |
| `pravah.wsAllowedOrigins` | WebSocket CORS origins | `http://localhost:5173,...` |
| `pravah.frontendBaseUrl` | Frontend URL for emails | `http://localhost:5173` |
| `pravah.hooksBaseUrl` | Public webhook URL prefix for scheduler | gateway in-cluster default |
| `pravah.rateLimitFailOpen` | Allow traffic when Redis errors (`false` in prod) | `"true"` |
| `pravah.authRefreshCookieSecure` | HttpOnly refresh cookie Secure flag | `"false"` (`"true"` in prod) |
| `pravah.runnerBootstrapExistingSecret` | Secret with runner registration bootstrap key | `""` |
| `services.runner-service.exposeGrpc` | LoadBalancer for external runner agents (gRPC 9091) | disabled |
| `networkPolicy.allowExternalEgress` | Egress to RDS/MSK/ElastiCache/S3/SMTP | `false` (`true` in prod) |
| `requireProductionSecrets` | Fail `helm template` on missing prod config | `false` (`true` in values-prod) |

### Security

| Parameter | Description | Default |
|-----------|-------------|---------|
| `podSecurityContext.runAsNonRoot` | Run as non-root | `true` |
| `podSecurityContext.runAsUser` | UID | `1000` |
| `containerSecurityContext.readOnlyRootFilesystem` | Read-only root | `true` |
| `networkPolicy.enabled` | Enable network policies | `false` |

### High Availability

| Parameter | Description | Default |
|-----------|-------------|---------|
| `services.<name>.replicaCount` | Replica count per service | `1` |
| `podDisruptionBudget.enabled` | Enable PDBs | `false` |
| `autoscaling.enabled` | Enable HPAs | `false` |
| `topologySpreadConstraints.enabled` | Enable zone spread | `false` |

## Production checklist

When `requireProductionSecrets: true` (`values-prod.yaml`), Helm **fails fast** unless:

- External Postgres, Kafka, Redis, and S3 artifact store are configured
- `image.tag` is a pinned SHA/release (not `latest`)
- `pravah.wsAllowedOrigins`, `frontendBaseUrl`, and `hooksBaseUrl` are set
- `smtp.host` is set (tenant password reset + alert emails)
- `pravah.rateLimitFailOpen=false` and `authRefreshCookieSecure=true`
- Dedicated `runner-bootstrap-secret` in credentials Secret
- Bundled MinIO and Mailhog are disabled

## Alpha Limitations

- **Container stages** disabled (`PRAVAH_CONTAINER_ENABLED=false`) — no Docker socket in pods
- **Argo CD** production auto-sync disabled by default (manual sync until staging validation); see `deploy/argocd/README.md`
- **HPA** and **PDB** require `replicaCount > 1` to be effective

## Uninstall

```bash
helm uninstall pravah -n pravah
kubectl delete namespace pravah
```

## Troubleshooting

### Pods not starting

```bash
kubectl -n pravah describe pod <pod-name>
kubectl -n pravah logs <pod-name> --previous
```

### Database connection issues

```bash
# Check postgres pod
kubectl -n pravah logs pravah-postgres-0

# Test connectivity from service pod
kubectl -n pravah exec -it <service-pod> -- sh -c "pg_isready -h pravah-postgres -U pravah"
```

### Image pull errors

```bash
kubectl -n pravah get events --field-selector reason=Failed
kubectl -n pravah describe secret ghcr-pull-secret
```

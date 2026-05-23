# Pravah Deployment (Helm)

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

**Production secrets (Vault):** Set `externalVault.address` and `services.pipeline-service.needsVault: true` with `vaultKubernetesRole` matching your Vault Kubernetes auth role. Pipeline resolves `vault:path#key` references via token auth (bundled dev) or Kubernetes auth (production).

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

Runner agent (outside cluster): `--tls-enabled --tls-trust-cert=ca.crt --tls-client-cert=client.crt --tls-client-key=client.key` (files from `deploy/certs/runner-grpc/`).

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
- **Argo CD** GitOps documented in ADR-010 but not wired (beta scope)
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

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

**Alpha services:** `gateway`, `tenant-service`, `pipeline-service`, `execution-service`, `scheduler-service`.

**Bundled dependencies (disable in production):** PostgreSQL 16, Apache Kafka 3.7 (KRaft), Redis 7.

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

### 4. Wait for Ready

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

# Pravah application secret
kubectl -n pravah create secret generic pravah-credentials \
  --from-literal=postgres-username=pravah \
  --from-literal=postgres-password='<secure-password>' \
  --from-literal=internal-service-secret='<32-char-secret>'

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
  --set pravah.wsAllowedOrigins=https://app.pravah.io \
  --set pravah.frontendBaseUrl=https://app.pravah.io \
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

### Application

| Parameter | Description | Default |
|-----------|-------------|---------|
| `pravah.jwtIssuer` | JWT issuer | `pravah-dev` |
| `pravah.internalServiceSecret` | Inter-service auth secret (chart Secret) | `pravah-local-internal-secret` |
| `pravah.internalServiceExistingSecret` | Use pre-created Secret instead of chart | `""` (prod: `pravah-credentials`) |
| `requireProductionSecrets` | Fail template if prod secrets missing | `false` (`true` in values-prod) |
| `pravah.wsAllowedOrigins` | WebSocket CORS origins | `http://localhost:5173,...` |
| `pravah.frontendBaseUrl` | Frontend URL for emails | `http://localhost:5173` |

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

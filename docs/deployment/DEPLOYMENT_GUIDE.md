# Pravah Deployment Guide

Complete instructions for running Pravah locally and in the cloud. Pick the path that matches your goal.

| Goal | Recommended path | Cost |
|------|------------------|------|
| Fastest local dev (edit code, run tests) | [A — Docker Compose + bare metal](#a-docker-compose--bare-metal-fastest) | **Free** |
| Full platform in Kubernetes locally | [B — kind / minikube + Helm](#b-kubernetes-local-kind--minikube) | **Free** |
| GitOps workflow locally | [C — Argo CD on kind](#c-argocd-gitops-local) | **Free** |
| Always-free cloud VM | [D — Oracle Cloud + k3s](#d-always-free-cloud-oracle-cloud--k3s) | **Free** (always) |
| Hybrid free-tier managed services | [E — Free-tier Postgres/Redis/Kafka](#e-hybrid-free-tier-managed-services) | **Free tier** (limits apply) |
| Production AWS | [F — Terraform + EKS + Helm](#f-production-aws-terraform--eks) | **Paid** (~$800+/mo staging) |
| Observability (metrics + traces) | [G — Observability stack](#g-observability-stack) | **Free** locally |

**Demo credentials (after seed):** `dev@localhost.pravah` / `PravahDev1!`

---

## Prerequisites

| Tool | Version | Used by |
|------|---------|---------|
| **JDK** | 21 | Backend services |
| **Docker** | 24+ | Compose, kind image builds |
| **Node.js** | 22 | Web UI |
| **Go** | 1.22+ | CLI (optional) |
| **kubectl** | 1.27+ | Kubernetes |
| **Helm** | 3.12+ | Chart install |
| **kind** or **minikube** | latest | Local K8s (path B/C) |
| **Terraform** | 1.5+ | AWS cloud (path F) |
| **AWS CLI** | v2 | EKS kubeconfig (path F) |

Verify:

```bash
java -version          # 21
docker info
node -v                # v22.x
kubectl version --client
helm version
```

---

## A. Docker Compose + bare metal (fastest)

Best for **daily development**: hot reload on web, fast Gradle rebuilds, no Kubernetes overhead.

### Step 1 — Clone and one-time setup

```bash
git clone https://github.com/Abhishekkumar2021/Pravah.git
cd Pravah
make local-setup    # copies .env examples for backend + web
```

### Step 2 — Start infrastructure

Starts Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog, Vault (dev mode):

```bash
make local-up
```

Services:

| Service | URL / port |
|---------|------------|
| Postgres | `localhost:5432` |
| Kafka | `localhost:9092` |
| Redis | `localhost:6379` |
| Jaeger UI | http://localhost:16686 |
| MinIO console | http://localhost:9001 |
| Mailhog (email) | http://localhost:8025 |
| Vault | http://localhost:8200 |

### Step 3 — Start Java services

In one terminal:

```bash
make local-services
```

Starts: tenant → pipeline → execution → scheduler → gateway (+ notification, connect, runner-service as configured).

Gateway: **http://localhost:8080**

Optional tracing (exports to local Jaeger):

```bash
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
export MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces
make local-services
```

### Step 4 — Seed demo data (once per fresh DB)

```bash
make local-seed
```

### Step 5 — Web UI

In another terminal:

```bash
make local-web
```

Open **http://localhost:5173** — sign in with demo credentials above.

### Step 6 — CLI (optional)

```bash
cd cli && make build && ./bin/pravah login
pravah workflow list
```

### Stop

```bash
make local-services-stop   # stop Java processes
make local-down            # stop Docker Compose
```

More detail: [backend/README.md](../backend/README.md), [web/README.md](../web/README.md).

---

## B. Kubernetes local (kind / minikube)

Best for **testing Helm charts**, production-like networking, and CI parity.

### Option B1 — kind (recommended, matches CI)

```bash
# 1. Create cluster (needs ~8 GB RAM free)
kind create cluster --name pravah

# 2. Build and load 8 service images
./scripts/deploy/k8s-local-build.sh

# 3. Install Helm chart
./scripts/deploy/k8s-local-install.sh

# 4. Verify all pods healthy
./scripts/deploy/k8s-local-smoke.sh pravah

# 5. Seed demo data
./scripts/deploy/k8s-local-seed.sh pravah

# 6. Access API
kubectl -n pravah port-forward svc/pravah-gateway 8080:8080
```

Web UI (separate terminal):

```bash
cd web && VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

### Option B2 — minikube

```bash
minikube start --cpus=4 --memory=8192
eval $(minikube docker-env)    # build images into minikube's Docker
./scripts/deploy/k8s-local-build.sh
./scripts/deploy/k8s-local-install.sh
./scripts/deploy/k8s-local-smoke.sh pravah
```

### Option B3 — Manual Helm

```bash
helm upgrade --install pravah deploy/helm/pravah-platform \
  --namespace pravah --create-namespace \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml
```

Bundled in cluster: Postgres, Kafka, Redis, MinIO, Mailhog, Vault dev.

More detail: [deploy/README.md](../deploy/README.md).

---

## C. Argo CD GitOps (local)

Reconciles the Helm chart from Git instead of direct `helm upgrade`.

```bash
./scripts/deploy/k8s-local-build.sh
./scripts/deploy/k8s-local-argocd.sh pravah
./scripts/deploy/k8s-local-smoke.sh pravah
```

Argo CD UI:

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
# Login: admin / (kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)
```

**Note:** Argo CD reads from the **Git branch** (`develop` by default), not your working tree. Push chart changes before expecting sync.

Manifests: `deploy/argocd/`

---

## D. Always-free cloud (Oracle Cloud + k3s)

Run the **full bundled stack** (Postgres/Kafka/Redis inside the cluster) on Oracle Cloud's **Always Free** ARM VM — no credit card charges after free tier if you stay within limits.

### Why this works

- Oracle Cloud Always Free: up to **4 OCPU + 24 GB RAM** on Ampere A1 (enough for kind/k3s + Pravah with bundled deps).
- Use `values-local.yaml` — no paid RDS/MSK/ElastiCache required.

### Steps (summary)

1. Create an Oracle Cloud account → create an **Ampere A1** VM (Ubuntu 22.04, 4 OCPU, 24 GB RAM).
2. Open ports: 22 (SSH), 6443 (K8s API), 80/443 (optional ingress).
3. Install k3s:

   ```bash
   curl -sfL https://get.k3s.io | sh -
   export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
   ```

4. Install Helm on the VM.
5. Clone repo, build images locally on the VM (or push to GHCR and pull):

   ```bash
   git clone https://github.com/Abhishekkumar2021/Pravah.git
   cd Pravah
   ./scripts/deploy/k8s-local-build.sh   # requires Docker on VM
   ./scripts/deploy/k8s-local-install.sh
   ./scripts/deploy/k8s-local-smoke.sh pravah
   ```

6. Expose gateway via **Cloudflare Tunnel** (free) or VM public IP + NodePort:

   ```bash
   kubectl -n pravah port-forward svc/pravah-gateway 8080:8080 --address 0.0.0.0
   ```

### Other always-free options

| Provider | Free offering | Pravah fit |
|----------|---------------|------------|
| **Oracle Cloud A1** | 4 OCPU, 24 GB RAM forever | Best — full K8s + bundled stack |
| **Google Cloud e2-micro** | 1 vCPU, 1 GB RAM | Too small for 8 JVM services |
| **AWS EC2 t2/t3.micro** | 12 months free | Too small; EKS control plane is **not** free |
| **Fly.io** | Limited free machines | Not sized for full platform |
| **Railway / Render** | Trial credits only | Not suitable for full stack |

---

## E. Hybrid free-tier managed services

Use **free-tier external databases** with Kubernetes (local kind or a free VM). Disable bundled Postgres/Redis/Kafka in Helm.

### Free-tier services (2026)

| Service | Provider | Free tier | Helm key |
|---------|----------|-----------|----------|
| PostgreSQL | [Neon](https://neon.tech) | 0.5 GB, 1 project | `externalPostgres.host` |
| Redis | [Upstash](https://upstash.com) | 10k cmds/day | `externalRedis.host` |
| Kafka | [Upstash Kafka](https://upstash.com) or [Confluent Cloud](https://confluent.cloud) | Limited throughput | `externalKafka.bootstrapServers` |
| Object storage | [Cloudflare R2](https://developers.cloudflare.com/r2/) | 10 GB/month | `externalArtifact.endpoint` |
| Email | [Mailgun](https://mailgun.com) sandbox / Mailhog in cluster | Dev only | `smtp.host` |

### Example Helm overrides

After creating accounts and collecting connection strings:

```bash
helm upgrade --install pravah deploy/helm/pravah-platform \
  --namespace pravah --create-namespace \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml \
  --set postgres.enabled=false \
  --set kafka.enabled=false \
  --set redis.enabled=false \
  --set minio.enabled=false \
  --set externalPostgres.host=<neon-host> \
  --set externalPostgres.port=5432 \
  --set externalRedis.host=<upstash-redis-host> \
  --set externalRedis.port=6379 \
  --set externalKafka.bootstrapServers=<upstash-kafka-endpoint> \
  --set externalArtifact.endpoint=https://<account>.r2.cloudflarestorage.com
```

Create K8s secrets for passwords before install (see [deploy/README.md](../deploy/README.md) Production secrets section).

Run `./scripts/deploy/terraform-init-rds.sh` equivalent manually on Neon (run `backend/scripts/postgres-init.sql` per service DB).

**Cost note:** Free tiers have strict limits. Suitable for demos and light testing, not production load.

---

## F. Production AWS (Terraform + EKS)

Full managed infrastructure path (pathway #10). **Paid** — staging estimate ~$820–850/month without MSK.

> **Status:** Terraform modules are on `develop` (`deploy/terraform/aws/`).

### Flow

```
terraform apply → EKS kubeconfig → RDS init → K8s secrets → Helm/Argo CD (PgBouncer → RDS) → smoke test
```

### Step 1 — Terraform

```bash
cd deploy/terraform/aws/reference
cp terraform.tfvars.example terraform.tfvars
# Edit: artifacts_bucket_name (globally unique), region, instance sizes

terraform init
terraform plan
terraform apply

aws eks update-kubeconfig --name "$(terraform output -raw eks_cluster_name)" --region us-east-1
```

### Step 2 — Database and secrets

```bash
# From repo root (after apply):
./scripts/deploy/terraform-create-k8s-secrets.sh
./scripts/deploy/terraform-init-rds.sh
./scripts/deploy/terraform-helm-bridge.sh   # prints Helm --set flags
```

### Step 3 — Helm production install

```bash
helm upgrade --install pravah deploy/helm/pravah-platform \
  --namespace pravah --create-namespace \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-prod.yaml \
  $(./scripts/deploy/terraform-helm-bridge.sh | grep '^--set')
```

Or use Argo CD production Application: `deploy/argocd/applications/pravah-platform-production.yaml`

### Step 4 — Verify

```bash
kubectl -n pravah get pods
./scripts/deploy/k8s-local-smoke.sh pravah   # works against any cluster with correct namespace
```

Detail: [deploy/terraform/README.md](../deploy/terraform/README.md), [deploy/README.md](../deploy/README.md).

### AWS free tier reality

| Resource | Free tier? |
|----------|------------|
| EKS control plane | **No** (~$73/mo) |
| EC2 nodes | 12-month t2/t3.micro only (too small for Pravah) |
| RDS | 12-month db.t3.micro (too small for 7 service DBs) |
| ElastiCache | **No** |
| S3 | 5 GB free (12 months) |

**Conclusion:** AWS production path is **not free**. Use path D or E for zero-cost demos.

---

## G. Observability stack

Install Prometheus, Grafana, and Jaeger (pathway #11).

### Kubernetes

```bash
./scripts/deploy/k8s-local-observability.sh

helm upgrade --install pravah deploy/helm/pravah-platform \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml \
  -f deploy/helm/pravah-platform/values-observability.yaml \
  --namespace pravah --create-namespace

kubectl -n monitoring port-forward svc/prometheus-grafana 3000:80
kubectl -n monitoring port-forward svc/jaeger-query 16686:16686
```

Grafana: http://localhost:3000 (`admin` / `admin`) → dashboard **Pravah Platform Overview**

### Docker Compose

Jaeger is already in `make local-up`. Set tracing env vars before `make local-services` (see path A).

Detail: [deploy/observability/README.md](../deploy/observability/README.md)

---

## Verification checklist

After any install path:

| Check | Command |
|-------|---------|
| Gateway health | `curl -s http://localhost:8080/actuator/health` |
| Login | Sign in at web UI or `curl -X POST http://localhost:8080/api/v1/auth/login ...` |
| K8s smoke | `./scripts/deploy/k8s-local-smoke.sh pravah` |
| Prometheus targets | Grafana → Explore → `up{job=~".*pravah.*"}` |
| CI parity | `./scripts/pre-commit.sh` |

---

## Troubleshooting

### Pods not ready (Kubernetes)

```bash
kubectl -n pravah get pods
kubectl -n pravah describe pod <name>
kubectl -n pravah logs <name> --previous
```

Common causes: insufficient cluster memory (need 8 GB+ for kind), images not loaded (`./scripts/deploy/k8s-local-build.sh`).

### Database connection errors

With PgBouncer enabled (`values-local.yaml` / `values-prod.yaml`), services use port **6432**, not Postgres/RDS port directly:

```bash
kubectl -n pravah logs deploy/pravah-pgbouncer
kubectl -n pravah logs pravah-postgres-0   # bundled Postgres only
kubectl -n pravah exec -it deploy/pravah-tenant-service -- sh -c 'wget -qO- http://localhost:8082/actuator/health'
```

Validate Helm wiring: `./scripts/deploy/validate-pgbouncer.sh`.

### Port already in use

Stop conflicting processes or change port-forward local port: `8081:8080`.

### Argo CD out of sync

Push chart changes to Git. Argo CD reads remote `develop`, not local files.

### Rate limiting fail-open warnings

Redis unreachable → gateway allows traffic. Check Redis pod/connectivity. In production set `pravah.rateLimitFailOpen=false` (requires healthy Redis).

---

## Quick reference — Makefile targets

| Command | Description |
|---------|-------------|
| `make local-setup` | One-time env file setup |
| `make local-up` | Docker Compose infrastructure |
| `make local-services` | Start Java backend |
| `make local-seed` | Demo tenant + workflows |
| `make local-web` | Vite dev server |
| `make k8s-local-build` | Build + load K8s images |
| `make k8s-local-install` | Helm install (local values) |
| `make k8s-local-smoke` | Health check all services |
| `make k8s-local-argocd` | Install Argo CD + Application |
| `make validate-argocd` | Validate Argo CD YAML |
| `make validate-observability` | Validate observability configs |
| `make pre-commit` | Full CI-parity checks |

---

## Related documentation

| Document | Content |
|----------|---------|
| [deploy/README.md](../deploy/README.md) | Helm chart reference, production checklist |
| [deploy/terraform/README.md](../deploy/terraform/README.md) | AWS modules, cost estimates |
| [deploy/observability/README.md](../deploy/observability/README.md) | Metrics, dashboards, tracing |
| [backend/README.md](../backend/README.md) | Service ports, env vars |
| [web/README.md](../web/README.md) | UI routes, Vite env |
| [cli/README.md](../cli/README.md) | CLI install and commands |
| [docs/IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md) | What is built vs planned |

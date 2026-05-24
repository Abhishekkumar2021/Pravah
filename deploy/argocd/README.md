# Argo CD GitOps (pathway #9)

GitOps manifests for reconciling `deploy/helm/pravah-platform` from Git per [ADR-010](../../docs/adr/ADR-010-kubernetes-helm-argocd.md).

## Layout

| Path | Purpose |
|------|---------|
| `appproject-pravah.yaml` | AppProject RBAC — allows `pravah` and `pravah-ci` destination namespaces |
| `applications/pravah-platform-local.yaml` | Local/dev: auto-sync from `develop` + `values-local.yaml` |
| `applications/pravah-platform-ci.yaml` | CI/kind smoke: `values-local.yaml` + `values-ci.yaml` |
| `applications/pravah-platform-production.yaml` | Production: manual sync from `main` + `values-prod.yaml` |
| `applications/pravah-platform-production.yaml.example` | Annotated copy template for fork-specific edits |
| `vault-policies/pravah-pipeline-service.hcl` | Example Vault policy for external Vault + pipeline-service K8s auth |

## Local (kind)

```bash
./scripts/deploy/k8s-local-build.sh          # load :local images into kind
./scripts/deploy/k8s-local-argocd.sh pravah  # install Argo CD v2.13.3 + Application
./scripts/deploy/k8s-local-smoke.sh pravah
```

Argo CD UI (after port-forward):

```bash
kubectl -n argocd port-forward svc/argocd-server 8443:443
# admin password: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
```

**Important:** Argo CD reads the chart from **Git** (`develop` by default), not your working tree. Push chart changes before expecting sync.

## Production

1. Install Argo CD in the cluster (operator choice — Helm, manifest, or managed).
2. Apply `appproject-pravah.yaml`, then `applications/pravah-platform-production.yaml`.
3. Configure external Postgres/Kafka/Redis/S3/SMTP/Vault in `values-prod.yaml` (or override via Argo CD parameters).
4. Create Kubernetes secrets listed in [deploy/README.md](../README.md#production-deployment).
5. Sync manually in Argo CD UI/CLI until staging validation passes; then enable automated sync in the Application manifest.

Image tags: the **Deploy · GitOps image tag** job (`.github/workflows/deploy.yml`) commits the pinned `image.tag` to `values-prod.yaml` on every `main` push that builds backend images. Argo CD picks up the change on the next sync.

Rollback: Git revert of `values-prod.yaml` or `argocd app rollback pravah-platform-production`.

## Vault (external)

When `pipeline-service.needsVault: true` in `values-prod.yaml`, pipeline-service uses Kubernetes auth to Vault. Apply the example policy and bind role `pravah-pipeline-service`:

```bash
vault policy write pravah-pipeline-service deploy/argocd/vault-policies/pravah-pipeline-service.hcl
# bind K8s SA: vault write auth/kubernetes/role/pravah-pipeline-service ...
```

See [deploy/README.md](../README.md) for full Vault KV + Transit setup.

## Validation & CI

```bash
make validate-argocd          # offline YAML checks (no cluster)
make k8s-ci-smoke-argocd      # kind + Argo CD sync + platform smoke
```

Nightly/PR workflow **K8s · Argo CD smoke** (`.github/workflows/k8s-smoke-nightly.yml`) runs the same path; logs on failure: `build/k8s-ci-smoke-argocd/`.

Helm CI (`.github/workflows/helm-ci.yml`) validates `deploy/argocd/**` on PRs.

## Gateway note

Tenant secrets API (`/api/v1/secrets/**`) must route through the API gateway to pipeline-service. The gateway route is covered by `GatewayPipelineRoutesTest` in pipeline-service pathway #8 parity.

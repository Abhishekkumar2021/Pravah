# ADR-010: Kubernetes + Helm + Argo CD for Deployment

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah is a microservices system (ADR-001) with 6+ services, each with its own deployment lifecycle. The deployment infrastructure must address:

- **Reproducibility**: the same configuration that worked in staging must deploy identically to production. Environment-specific differences should be limited to parameterized values, not structural differences.
- **Rollback**: if a deployment introduces a regression, reverting to the previous version must be fast (under 5 minutes) and reliable.
- **Auditability**: who deployed what, when, and from which source revision? Compliance requires answering these questions.
- **GitOps**: the declared state of the cluster should live in Git. The cluster should continuously reconcile toward that state. Manual `kubectl apply` operations are not auditable and drift from the Git state.
- **Progressive delivery**: new versions should be rolled out gradually — canary to 5% of traffic first, then promoted or rolled back based on metrics.
- **On-premise deployment**: Pravah's runner registers with the cloud control plane, but the control plane itself must also be deployable on-premise for enterprise customers with air-gapped environments.

---

## Decision

**Kubernetes** as the container orchestration platform. **Helm** for packaging service configurations. **Argo CD** for GitOps-based continuous delivery.

**Layer responsibilities:**

```
Git Repository (source of truth)
    │
    │  Helm chart templates + values per environment
    │
    ▼
Argo CD (reconciliation engine)
    │
    │  Detects drift between Git state and cluster state
    │  Applies changes automatically (or with approval gate)
    │
    ▼
Kubernetes API Server
    │
    │  Schedules pods, manages networking, enforces policies
    │
    ▼
Nodes (compute)
    │
    │  Run containers, mount volumes, enforce cgroups
    │
    ▼
Running Services
```

**Helm chart structure:**

Each service has its own Helm chart. A top-level umbrella chart (`pravah-platform`) references all service charts:

```
charts/
├── pravah-platform/           # Umbrella chart
│   ├── Chart.yaml
│   ├── values.yaml            # Default values
│   ├── values-staging.yaml    # Staging overrides
│   ├── values-prod.yaml       # Production overrides
│   └── charts/
│       ├── execution-service/ # Subchart
│       ├── pipeline-service/
│       ├── scheduler-service/
│       ├── runner-service/
│       └── api-gateway/
│
└── execution-service/         # Standalone chart (also used by umbrella)
    ├── Chart.yaml
    ├── values.yaml
    └── templates/
        ├── deployment.yaml
        ├── service.yaml
        ├── hpa.yaml
        ├── networkpolicy.yaml
        └── configmap.yaml
```

**Argo CD Application configuration:**

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: pravah-production
  namespace: argocd
spec:
  project: pravah
  source:
    repoURL: https://github.com/Abhishekkumar2021/Pravah
    targetRevision: main
    path: charts/pravah-platform
    helm:
      valueFiles:
        - values-prod.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: pravah
  syncPolicy:
    automated:
      selfHeal: true      # Drift from Git state is automatically corrected
      prune: true         # Resources removed from Git are removed from cluster
    syncOptions:
      - CreateNamespace=true
```

**Deployment workflow:**

```
Developer merges PR to main
    │
    │  CI: build image → push to registry → update image tag in values-prod.yaml
    │
    ▼
Git: values-prod.yaml updated with new image tag
    │
    ▼
Argo CD detects drift (cluster has old tag, Git has new tag)
    │
    ▼
Argo CD applies Helm chart with new values
    │
    ▼
Kubernetes performs rolling update (maxUnavailable=0, maxSurge=1)
    │
    ▼
Readiness probes gate traffic to new pods
    │
    ▼
Old pods terminated after new pods are ready
```

**Rollback procedure:**

```bash
# Option 1: Argo CD rollback (via UI or CLI)
argocd app rollback pravah-production <revision-number>

# Option 2: Git revert (preferred — maintains Git as source of truth)
git revert <commit-that-updated-image-tag>
git push origin main
# Argo CD automatically applies the revert
```

---

## Consequences

### Positive

- **Git is the source of truth**: every change to the cluster is a Git commit. The question "what changed and when?" is answered by `git log`. The question "who approved this change?" is answered by the PR merge record.
- **Drift detection and self-healing**: if a team member accidentally runs `kubectl apply` directly on the production cluster, Argo CD detects the drift and reverts it within minutes. The cluster is always in the state declared in Git.
- **Rollback is a Git operation**: reverting a bad deployment is as simple as reverting the commit that updated the image tag. Argo CD reconciles automatically. No special rollback runbook required.
- **Environment parity**: staging and production use the same Helm chart structure. The only differences are the values files. If it works in staging, it will work in production (assuming the values are correct).
- **Multi-environment promotion**: the GitOps workflow naturally supports staging → production promotion. A PR that updates `values-prod.yaml` is the promotion mechanism, with all the code review and approval controls that PRs provide.
- **Declarative Kubernetes resources**: Helm templates are composable, version-controlled, and diffable. `helm diff upgrade` shows exactly what will change before applying.

### Negative

- **Argo CD is additional infrastructure**: another component to operate, secure, and back up. Argo CD's own high availability must be maintained.
- **GitOps loop latency**: there is a delay between pushing to Git and the cluster being updated (Git push → Argo CD detects change → applies → rolling update). This is typically 1-3 minutes, which is acceptable for standard deployments but may feel slow compared to `kubectl apply` for emergency patches.
- **Helm template complexity grows**: as services mature, Helm templates accumulate conditionals, helpers, and edge-case configurations. Templates become harder to read. This is managed by keeping service charts small and using `_helpers.tpl` for shared logic.
- **Secret management integration**: Helm values must not contain secrets. Secrets are injected via Vault Agent (ADR-007); Helm only contains references (e.g., Vault role names), not secret values.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Argo CD cluster unavailable during emergency | `kubectl` with direct cluster access remains available; emergency changes are documented and then committed to Git post-incident |
| Auto-sync applies a broken chart | Staging deployment validates the chart before it reaches production; Argo CD sync can be paused for manual review in emergencies |
| Image tag mismatch between environments | Image digest (SHA) is used instead of mutable tags like `latest`; the CI system pins the exact digest in the values file |
| Chart drift between services over time | Shared chart library (`pravah-common`) provides base templates; services extend rather than duplicate |

---

## Alternatives Considered

### Raw kubectl + Shell Scripts

Maintain deployment scripts that run `kubectl apply -f` for each service. Simpler than Helm and Argo CD.

Rejected because:
- Raw YAML manifests have no parameterization — copying and adapting for each environment requires duplication or fragile sed replacements.
- No built-in rollback (must maintain previous manifest versions manually).
- No drift detection — manual `kubectl apply` operations go undetected and create configuration drift.
- No deployment audit trail beyond shell history.

### Flux CD (Alternative GitOps tool)

Flux is another popular GitOps operator for Kubernetes.

Considered. Argo CD chosen over Flux because:
- Argo CD has a richer UI for visualizing application state, sync status, and resource trees. This is valuable during incidents when understanding "what is deployed and is it healthy?" must be immediate.
- Argo CD's Application CRD provides a first-class concept of an application with health status, sync status, and history. Flux's model is more Git-centric and less application-centric.
- Both are CNCF projects and functionally equivalent for Pravah's use case. The decision is reversible.

### Terraform for Kubernetes resources

Use Terraform's Kubernetes provider to manage all Kubernetes resources.

Rejected because:
- Terraform state management for Kubernetes resources adds friction to the deployment cycle. Every deployment requires a `terraform apply` with state locking.
- Terraform is well-suited for infrastructure provisioning (cloud resources, Kubernetes clusters themselves). Managing individual Kubernetes workloads (Deployments, Services, HPAs) is better handled by Helm + Argo CD, which understands Kubernetes concepts natively.
- Terraform is used in Pravah for provisioning the underlying cloud infrastructure (VPCs, Kubernetes clusters, managed databases). Argo CD manages what runs on top of that infrastructure.

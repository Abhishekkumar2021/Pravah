# GitHub Actions

| Workflow | File | When it runs | Purpose |
|----------|------|--------------|---------|
| **Backend CI** | `backend-ci.yml` | PR/push to `main`/`develop` (backend paths) | Unit tests, Spotless, static analysis, integration tests, OWASP scan |
| **Frontend CI** | `frontend-ci.yml` | PR/push to `main`/`develop` (web paths) | TypeScript, Vitest, Playwright E2E, Vite build |
| **CLI CI** | `cli-ci.yml` | PR/push (`cli/**` paths) | golangci-lint, `go test -race`, build, GoReleaser check (main only) |
| **Pull Request** | `pull-request.yml` | Every PR | Conventional title, doc link check, size guard |
| **Deploy** | `deploy.yml` | Push to `main`, GitHub Release published | Build & push service/runner images to GHCR; bump `values-prod.yaml` image tag on `main` |
| **K8s Smoke** | `k8s-smoke-nightly.yml` | Nightly 03:00 UTC, PR (deploy/backend paths), manual | kind + Helm direct install + Argo CD GitOps sync + smoke |
| **Terraform** | `terraform-ci.yml` | PR/push (`deploy/terraform/**`) | `terraform fmt`, `init`, `validate` (no AWS apply) |
| **Release** | `release.yml` | Manual (`workflow_dispatch`) | Tag version, changelog, GitHub Release (then Deploy) |

## Required status checks (branch protection)

After this layout, prefer these **job names** in rulesets:

**Backend changes**

- `Backend · Unit tests`
- `Backend · Format & lint`
- `Backend · Integration tests` (optional if Docker not available on forks)

**Frontend changes**

- `Frontend · Lint, test & build`

**CLI changes**

- `Lint`
- `Test`
- `Build`

**All PRs**

- `PR · Conventional title`

Remove stale entries such as `Build & Test`, `Code Quality`, `Web Build`, or `Build Check` from older workflows.

## Local parity

```bash
# Full suite (auto-detects changed backend/, web/, cli/ paths)
./scripts/pre-commit.sh

# Per component (matches CI workflows)
./scripts/ci-backend.sh
./scripts/ci-web.sh
./scripts/ci-cli.sh

# Force every component
FORCE_ALL=1 ./scripts/pre-commit.sh
```

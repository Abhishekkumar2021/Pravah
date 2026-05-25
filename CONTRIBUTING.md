# Contributing to Pravah

Thank you for your interest in contributing to Pravah! This document provides guidelines and instructions for contributing.

## Branch Strategy

We follow a **Git Flow** branching model:

```
main (protected)
  │
  └── develop (protected)
        │
        ├── feature/xxx
        ├── fix/xxx
        └── refactor/xxx
```

### Branch Types

| Branch | Purpose | Base | Merges Into |
|--------|---------|------|-------------|
| `main` | Production-ready code | - | - |
| `develop` | Integration branch | `main` | `main` (via release) |
| `feature/*` | New features | `develop` | `develop` |
| `fix/*` | Bug fixes | `develop` | `develop` |
| `hotfix/*` | Urgent production fixes | `main` | `main` and `develop` |
| `refactor/*` | Code improvements | `develop` | `develop` |
| `docs/*` | Documentation changes | `develop` | `develop` |

### Branch Protection Rules

**`main` branch:**
- No direct pushes allowed
- Requires pull request with at least 1 approval
- Requires code owner review
- Requires passing CI (see `.github/workflows/README.md` for job names)
- Enforces linear history (no merge commits)
- Force pushes disabled

**`develop` branch:**
- No direct pushes allowed
- Requires passing CI (backend and/or frontend jobs as applicable)
- Force pushes disabled

**Important:** The bullets above are **policy**. GitHub will only block direct pushes after rules are configured on the server. Docs alone do not enforce anything.

For a **solo maintainer**, GitHub **branch rulesets** can still require PRs and CI for day-to-day quality, while **Allow repository admins to bypass** (or an equivalent bypass entry for the repository admin role) lets you merge or push when you are blocked by checks—use sparingly.

### Enforcing PR-only workflow (repository administrators)

Do this in GitHub for **each** protected branch (`main`, `develop`). Prefer **rulesets** (Settings → Rules → Rulesets); they supersede classic branch protection where both exist.

#### Option A — Branch rulesets (recommended)

1. Open the repo on GitHub → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. **Ruleset name:** e.g. `Protect main and develop`.
3. **Enforcement status:** Active.
4. **Target branches:** Add targets → **Include by pattern** → enter `main` → add another pattern → `develop`.
5. Enable at least:
   - **Require a pull request before merging** (set minimum number of approvals and “dismiss stale reviews” as you prefer).
   - **Require status checks to pass** → add job names from `.github/workflows/` (see `.github/workflows/README.md`; e.g. `Backend · Unit tests`, `Frontend · Lint, test & build`, `PR · Conventional title`).
   - **Block force pushes**.
6. **Bypass list (solo developer):** enable **Repository admin** (or **Repository role: Admin**) so the owner can bypass when needed. Omit this if you want the strictest possible setup with no bypasses.
7. Save the ruleset. With PR required and optional admin bypass, normal work goes through PRs; direct pushes stay blocked unless you use bypass (e.g. merge with admin override or adjust rules in Settings).

#### Option B — Classic branch protection

1. **Settings** → **Branches** → **Add branch protection rule** (or edit existing).
2. **Branch name pattern:** `main` (repeat for a second rule on `develop`).
3. Enable:
   - **Require a pull request before merging**
   - **Require approvals** (e.g. 1) where you want review
   - **Require status checks to pass before merging** (select the same CI checks as in Option A)
   - For a solo repo, **do not** enable “Do not allow bypassing the above settings” if you want the owner to be able to override when needed.
4. Enable **Block force pushes**.

#### Day-to-day for contributors

- Never commit to `develop` or `main` directly. Always: `git checkout -b feature/...` (or `fix/...`) from up-to-date `develop`, push the **topic branch**, open a PR into `develop` (or `main` for hotfix/release per your process).

## Development Workflow

### 1. Create a Feature Branch

```bash
# Ensure you're on develop and up-to-date
git checkout develop
git pull origin develop

# Create your feature branch
git checkout -b feature/your-feature-name
```

### 2. Make Changes

Follow the coding standards defined in:
- `.cursor/rules/` - Cursor AI rules
- `docs/lld/` - Low-level design patterns
- `docs/adr/` - Architecture decisions
- `docs/IMPLEMENTATION_STATUS.md` - Update when shipping or removing user-facing capabilities

**Local stack:** from repo root, `make local-setup && make local-up && make local-services && make local-seed` (see [backend/README.md](backend/README.md)).

### 3. Commit Your Changes

We use conventional commits:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting (no code change)
- `refactor`: Code restructuring
- `perf`: Performance improvement
- `test`: Adding tests
- `build`: Build system changes
- `ci`: CI/CD changes
- `chore`: Other changes

**Examples:**
```bash
git commit -m "feat(pipeline): add YAML validation for pipeline definitions"
git commit -m "fix(execution): resolve race condition in job scheduling"
git commit -m "docs(api): update GraphQL schema documentation"
```

### 4. Push and Create PR

```bash
# Push your branch
git push -u origin feature/your-feature-name

# Create PR via GitHub CLI (subject after ":" must start with A–Z — see PR title rules below)
gh pr create --base develop --title "feat(pipeline-service): Add YAML validation for definitions" --body "Description..."
```

### 5. PR Review Process

1. Automated checks run (CI)
2. Code review by maintainers
3. Address feedback
4. Merge when approved

## Code Quality Requirements

Before submitting a PR, ensure:

### Build & Test
```bash
cd backend
make build      # Build all modules
make test       # Run unit tests
make lint       # Check formatting
```

### Formatting
```bash
make format     # Auto-format code
```

### Local Infrastructure
```bash
make docker-up  # Start local services
make test-int   # Run integration tests
```

## Pull Request Guidelines

### PR Title

CI (`.github/workflows/pull-request.yml`, `amannn/action-semantic-pull-request`) enforces:

1. **Conventional-commit prefix:** `type(optional-scope): ` where `type` is one of the allowed types (same family as commits: `feat`, `fix`, `docs`, …).
2. **Subject (text after the colon and space):** must match `^[A-Z].+$` — the first character **must be an uppercase letter** (sentence case / title-style start), not lowercase.

Valid:

```
feat(execution-service): Cancel execution API (US-02.04)
fix(gateway): Route execution cancel to execution-service
```

Invalid (fails PR lint):

```
feat(execution-service): cancel execution API (US-02.04)
feat: your feature
```

Scope is optional in the action (`requireScope: false`) but **recommended** for service or component, matching commit scope.

### PR Description
Use the PR template and include:
- Summary of changes
- Type of change
- Related issues
- Documentation alignment check
- Testing checklist
- Security checklist (for multi-tenant changes)

### PR Size
- Keep PRs small and focused
- Target < 500 lines changed
- PRs > 1000 lines will trigger a warning
- PRs > 2000 lines will be blocked

## Testing Requirements

| Change Type | Unit Tests | Integration Tests |
|-------------|------------|-------------------|
| New feature | Required | Required |
| Bug fix | Required | If applicable |
| Refactor | Required | If applicable |
| Documentation | Not required | Not required |

### Test Coverage
- Minimum 80% coverage for new code
- No decrease in overall coverage

## Documentation

Update documentation when:
- Adding new features
- Changing APIs
- Modifying architecture
- Updating dependencies

Relevant docs:
- `docs/adr/` - Architecture Decision Records
- `docs/lld/` - Low-Level Design
- `docs/architecture/` - High-Level Architecture
- `docs/product/` - Product documentation

## Getting Help

- Create an issue for bugs or feature requests
- Use discussions for questions
- Tag maintainers for urgent issues

## Code of Conduct

Be respectful, inclusive, and professional in all interactions.

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
- Requires passing CI (Build & Test, Code Quality)
- Enforces linear history (no merge commits)
- Force pushes disabled

**`develop` branch:**
- No direct pushes allowed
- Requires passing CI (Build & Test)
- Force pushes disabled

**Important:** The bullets above are **policy**. GitHub will only block direct pushes after a **repository administrator** configures rules below. Docs alone do not enforce anything.

**As applied in this repo:** branch rulesets are versioned under [`.github/scripts/`](.github/scripts/README.md) (JSON + `gh api` instructions). Classic branch protection was removed in favor of rulesets so **admin bypass** does not allow direct pushes.

### Enforcing PR-only workflow (repository administrators)

Do this in GitHub for **each** protected branch (`main`, `develop`). Prefer **rulesets** (Settings → Rules → Rulesets); they supersede classic branch protection where both exist.

#### Option A — Branch rulesets (recommended)

1. Open the repo on GitHub → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. **Ruleset name:** e.g. `Protect main and develop`.
3. **Enforcement status:** Active.
4. **Target branches:** Add targets → **Include by pattern** → enter `main` → add another pattern → `develop`.
5. Enable at least:
   - **Require a pull request before merging** (set minimum number of approvals and “dismiss stale reviews” as you prefer).
   - **Require status checks to pass** → add the checks from `.github/workflows/` (e.g. build/test jobs from `ci.yml` / `pr-checks.yml`).
   - **Block force pushes**.
6. **Bypass list:** Leave empty, or only add automation accounts that truly must merge without PRs. **Do not** add humans here if you want no direct pushes for anyone.
7. In the ruleset, avoid granting **bypass** to “Repository admin” unless you explicitly want admins to push around the rules (that is how direct pushes slipped through before).

Save the ruleset. After this, `git push origin develop` from a laptop should be **rejected** unless the push is a merge from a merged PR (depending on how you merge) or you use a bypass-capable actor.

#### Option B — Classic branch protection

1. **Settings** → **Branches** → **Add branch protection rule** (or edit existing).
2. **Branch name pattern:** `main` (repeat for a second rule on `develop`).
3. Enable:
   - **Require a pull request before merging**
   - **Require approvals** (e.g. 1) where you want review
   - **Require status checks to pass before merging** (select the same CI checks as in Option A)
   - **Do not allow bypassing the above settings** (so administrators cannot push directly either—use this if you want strict PR-only).
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

# Create PR via GitHub CLI
gh pr create --base develop --title "feat: your feature" --body "Description..."
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
cd implementation
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
Follow conventional commit format:
```
feat(scope): brief description
fix(scope): brief description
```

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

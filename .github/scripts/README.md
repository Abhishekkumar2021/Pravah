# Repository rulesets (GitHub)

These JSON files define [branch rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets) applied to **Abhishekkumar2021/Pravah**.

| File | Branch | Summary |
|------|--------|---------|
| `ruleset-develop.json` | `develop` | PR required (0 approvals), block force-push & deletion, required checks from **PR Checks** workflow that run on every PR |
| `ruleset-main.json` | `main` | Same checks + **1 approval** + **CODEOWNERS** review |

**Required status checks** use job names from `.github/workflows/pr-checks.yml` that are **not** skipped by `if:` filters: `PR Lint`, `PR Size`, `Detect Changes`.

`Build & Test` and `Code Quality` from `ci.yml` are **not** listed here because that workflow is path-filtered; requiring them would block docs-only PRs. CI still runs when paths match; tighten rules later with an always-run gate job if you want merge blocking on Gradle.

## Apply or update (requires `repo` scope + admin on the repo)

```bash
cd "$(git rev-parse --show-toplevel)"
# Create (first time) — ignore 422 if rulesets already exist
gh api --method POST repos/Abhishekkumar2021/Pravah/rulesets --input .github/scripts/ruleset-develop.json || true
gh api --method POST repos/Abhishekkumar2021/Pravah/rulesets --input .github/scripts/ruleset-main.json || true

# Update existing (replace RULESET_ID from: gh api repos/Abhishekkumar2021/Pravah/rulesets -q '.[].id,.[].name')
gh api -X PUT repos/Abhishekkumar2021/Pravah/rulesets/RULESET_ID_DEVELOP --input .github/scripts/ruleset-develop.json
gh api -X PUT repos/Abhishekkumar2021/Pravah/rulesets/RULESET_ID_MAIN --input .github/scripts/ruleset-main.json
```

List rulesets:

```bash
gh api repos/Abhishekkumar2021/Pravah/rulesets --jq '.[] | {id, name, enforcement}'
```

## Classic branch protection

Legacy **branch protection** was removed so it would not allow **admin bypass** (`enforce_admins: false`). **Rulesets** are the single enforcement layer (`current_user_can_bypass: never`).

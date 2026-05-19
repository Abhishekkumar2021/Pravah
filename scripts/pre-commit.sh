#!/usr/bin/env bash
# Pravah pre-commit checks — backend + web + CLI (aligned with GitHub Actions CI).
#
# Usage (from repo root):
#   ./scripts/pre-commit.sh
#
# Component scripts (run individually):
#   ./scripts/ci-backend.sh
#   ./scripts/ci-web.sh
#   ./scripts/ci-cli.sh
#
# Optional skips:
#   SKIP_BACKEND=1      — skip backend
#   SKIP_WEB=1          — skip web
#   SKIP_CLI=1          — skip CLI
#   SKIP_INTEGRATION=1  — skip backend integrationTest (Docker/Testcontainers)
#   SKIP_E2E=1          — skip Playwright e2e
#   SKIP_OWASP=1        — skip OWASP dependency check (default in ci-backend.sh)
#   FORCE_ALL=1         — run all components even for docs-only diffs
#
# Requires: JDK 21, Docker (integration tests), Node.js 22, Go 1.22+
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

collect_changed_files() {
  {
    git -C "$ROOT" diff --name-only 2>/dev/null || true
    git -C "$ROOT" diff --name-only --cached 2>/dev/null || true
    if git -C "$ROOT" rev-parse origin/develop >/dev/null 2>&1; then
      git -C "$ROOT" diff --name-only origin/develop...HEAD 2>/dev/null || true
    fi
  } | sed '/^$/d' | sort -u
}

# DO_* = 1 means run that component's checks
DO_BACKEND=0
DO_WEB=0
DO_CLI=0

[[ "${SKIP_BACKEND:-}" == "1" ]] && DO_BACKEND=-1
[[ "${SKIP_WEB:-}" == "1" ]] && DO_WEB=-1
[[ "${SKIP_CLI:-}" == "1" ]] && DO_CLI=-1

files="$(collect_changed_files)"

if [[ "${FORCE_ALL:-}" == "1" ]] || [[ -z "$files" ]]; then
  [[ "$DO_BACKEND" != "-1" ]] && DO_BACKEND=1
  [[ "$DO_WEB" != "-1" ]] && DO_WEB=1
  [[ "$DO_CLI" != "-1" ]] && DO_CLI=1
else
  only_docs=1
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    case "$f" in
      docs/*|*.md|.cursor/*|.github/workflows/README.md)
        ;;
      *)
        only_docs=0
        break
        ;;
    esac
  done <<<"$files"

  if [[ "$only_docs" == "1" ]]; then
    echo "Docs-only changes detected — skipping backend, web, and CLI checks."
    echo "  (Set FORCE_ALL=1 to run the full suite anyway.)"
    exit 0
  fi

  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    case "$f" in
      backend/*|.github/workflows/backend-ci.yml|.github/workflows/deploy.yml)
        [[ "$DO_BACKEND" != "-1" ]] && DO_BACKEND=1
        ;;
      web/*|.github/workflows/frontend-ci.yml)
        [[ "$DO_WEB" != "-1" ]] && DO_WEB=1
        ;;
      cli/*|.github/workflows/cli-ci.yml)
        [[ "$DO_CLI" != "-1" ]] && DO_CLI=1
        ;;
      scripts/pre-commit.sh|scripts/ci-*.sh|.github/workflows/pull-request.yml)
        [[ "$DO_BACKEND" != "-1" ]] && DO_BACKEND=1
        [[ "$DO_WEB" != "-1" ]] && DO_WEB=1
        [[ "$DO_CLI" != "-1" ]] && DO_CLI=1
        ;;
    esac
  done <<<"$files"
fi

if [[ "$DO_BACKEND" != "1" && "$DO_WEB" != "1" && "$DO_CLI" != "1" ]]; then
  echo "No component checks selected (nothing to run)."
  echo "  Use FORCE_ALL=1 or change files under backend/, web/, or cli/."
  exit 0
fi

echo "Pre-commit plan: backend=$([[ $DO_BACKEND == 1 ]] && echo yes || echo no) web=$([[ $DO_WEB == 1 ]] && echo yes || echo no) cli=$([[ $DO_CLI == 1 ]] && echo yes || echo no)"
echo ""

if [[ "$DO_BACKEND" == "1" ]]; then
  export SKIP_OWASP="${SKIP_OWASP:-1}"
  "$ROOT/scripts/ci-backend.sh"
fi

if [[ "$DO_WEB" == "1" ]]; then
  "$ROOT/scripts/ci-web.sh"
fi

if [[ "$DO_CLI" == "1" ]]; then
  export SKIP_GORELEASER_CHECK="${SKIP_GORELEASER_CHECK:-1}"
  "$ROOT/scripts/ci-cli.sh"
fi

echo ""
echo "✅ All pre-commit checks passed — safe to commit"

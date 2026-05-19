#!/usr/bin/env bash
# Web checks — mirrors .github/workflows/frontend-ci.yml
#
# Usage (from repo root):
#   ./scripts/ci-web.sh
#
# Optional:
#   SKIP_E2E=1  — skip Playwright e2e
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB="$ROOT/web"

echo "=== Web: dependencies (npm ci) ==="
(cd "$WEB" && npm ci)

echo "=== Web: lint (TypeScript) ==="
(cd "$WEB" && npm run lint)

echo "=== Web: unit tests (Vitest) ==="
(cd "$WEB" && npm run test)

if [[ "${SKIP_E2E:-}" != "1" ]]; then
  echo "=== Web: Playwright browser ==="
  (cd "$WEB" && npx playwright install chromium)
  echo "=== Web: e2e tests (Playwright) ==="
  (cd "$WEB" && npm run test:e2e)
else
  echo "=== Web: e2e tests (skipped, SKIP_E2E=1) ==="
fi

echo "=== Web: production build ==="
(cd "$WEB" && npm run build)

echo "✅ Web checks passed"

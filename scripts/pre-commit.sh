#!/usr/bin/env bash
# Pravah pre-commit checks — backend + web (aligned with CI).
#
# Usage (from repo root):
#   ./scripts/pre-commit.sh
#
# Optional:
#   SKIP_INTEGRATION=1  — skip backend integrationTest (requires Docker/Testcontainers)
#   SKIP_E2E=1          — skip Playwright e2e (faster; CI still runs e2e on web changes)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== Backend: format (spotlessApply) ==="
(cd "$ROOT/backend" && ./gradlew spotlessApply --no-daemon)

echo "=== Backend: compile ==="
(cd "$ROOT/backend" && ./gradlew compileJava compileTestJava --no-daemon)

echo "=== Backend: unit tests ==="
(cd "$ROOT/backend" && ./gradlew test --no-daemon)

if [[ "${SKIP_INTEGRATION:-}" != "1" ]]; then
  echo "=== Backend: integration tests ==="
  (cd "$ROOT/backend" && ./gradlew integrationTest --no-daemon)
else
  echo "=== Backend: integration tests (skipped, SKIP_INTEGRATION=1) ==="
fi

echo "=== Web: dependencies (npm ci) ==="
(cd "$ROOT/web" && npm ci)

echo "=== Web: lint (TypeScript) ==="
(cd "$ROOT/web" && npm run lint)

echo "=== Web: unit tests (Vitest) ==="
(cd "$ROOT/web" && npm run test)

if [[ "${SKIP_E2E:-}" != "1" ]]; then
  echo "=== Web: Playwright browser ==="
  (cd "$ROOT/web" && npx playwright install chromium)
  echo "=== Web: e2e tests (Playwright) ==="
  (cd "$ROOT/web" && npm run test:e2e)
else
  echo "=== Web: e2e tests (skipped, SKIP_E2E=1) ==="
fi

echo "=== Web: production build ==="
(cd "$ROOT/web" && npm run build)

echo "✅ All pre-commit checks passed — safe to commit"

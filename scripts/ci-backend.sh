#!/usr/bin/env bash
# Backend checks — mirrors .github/workflows/backend-ci.yml
#
# Usage (from repo root):
#   ./scripts/ci-backend.sh
#
# Optional:
#   SKIP_INTEGRATION=1  — skip Testcontainers integration tests
#   SKIP_OWASP=1        — skip OWASP dependency check (default: skip; slow)
#   SKIP_APPLY=1        — skip spotlessApply (only run spotlessCheck)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"

if [[ "${SKIP_APPLY:-}" != "1" ]]; then
  echo "=== Backend: format (spotlessApply) ==="
  (cd "$BACKEND" && ./gradlew spotlessApply --no-daemon)
fi

echo "=== Backend: format verify (spotlessCheck) ==="
(cd "$BACKEND" && ./gradlew spotlessCheck --no-daemon)

echo "=== Backend: static analysis (check -x test) ==="
(cd "$BACKEND" && ./gradlew check -x test --no-daemon)

echo "=== Backend: compile ==="
(cd "$BACKEND" && ./gradlew compileJava compileTestJava --no-daemon)

echo "=== Backend: unit tests ==="
(cd "$BACKEND" && ./gradlew test --no-daemon)

if [[ "${SKIP_INTEGRATION:-}" != "1" ]]; then
  echo "=== Backend: integration tests ==="
  (cd "$BACKEND" && TESTCONTAINERS_RYUK_DISABLED=true ./gradlew integrationTest --no-daemon)
else
  echo "=== Backend: integration tests (skipped, SKIP_INTEGRATION=1) ==="
fi

if [[ "${SKIP_OWASP:-}" != "1" ]]; then
  echo "=== Backend: OWASP dependency check ==="
  (cd "$BACKEND" && ./gradlew dependencyCheckAnalyze --no-daemon) || {
    echo "::warning::OWASP dependency check failed (non-blocking in CI)"
  }
else
  echo "=== Backend: OWASP dependency check (skipped, SKIP_OWASP=1) ==="
fi

echo "✅ Backend checks passed"

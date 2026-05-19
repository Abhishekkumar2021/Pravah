#!/usr/bin/env bash
# CLI checks — mirrors .github/workflows/cli-ci.yml
#
# Usage (from repo root):
#   ./scripts/ci-cli.sh
#
# Requires:
#   - Go 1.22+ (GOTOOLCHAIN=go1.22.0 used when a newer default Go is installed)
#   - golangci-lint at version in cli/.golangci-version (one-time: cd cli && make install-tools)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI="$ROOT/cli"
VERSION_FILE="$CLI/.golangci-version"

export GOTOOLCHAIN="${GOTOOLCHAIN:-go1.22.0}"

require_golangci_lint() {
  if [[ ! -f "$VERSION_FILE" ]]; then
    echo "error: missing $VERSION_FILE" >&2
    exit 1
  fi

  local want
  want="$(tr -d '[:space:]' < "$VERSION_FILE")"

  if ! command -v golangci-lint >/dev/null 2>&1; then
    echo "error: golangci-lint is not on PATH." >&2
    echo "" >&2
    echo "Install the pinned version (${want}):" >&2
    echo "  cd cli && make install-tools" >&2
    echo "" >&2
    echo "Then ensure \$(go env GOPATH)/bin is on PATH:" >&2
    echo "  export PATH=\"\$(go env GOPATH)/bin:\$PATH\"" >&2
    exit 1
  fi

  local got
  got="$(golangci-lint version --format short 2>/dev/null || true)"
  if [[ "$got" != "$want" ]]; then
    echo "error: golangci-lint version mismatch (want ${want}, got ${got:-unknown})." >&2
    echo "  cd cli && make install-tools" >&2
    exit 1
  fi
}

echo "=== CLI: tidy modules ==="
(cd "$CLI" && go mod tidy)

echo "=== CLI: golangci-lint (${GOTOOLCHAIN}) ==="
require_golangci_lint
(cd "$CLI" && golangci-lint run ./...)

echo "=== CLI: unit tests (race) ==="
(cd "$CLI" && go test -race ./...)

echo "=== CLI: build ==="
(
  cd "$CLI"
  VERSION="$(git -C "$ROOT" describe --tags --always --dirty 2>/dev/null || echo dev)"
  COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  go build -ldflags "-X main.version=${VERSION} -X main.commit=${COMMIT} -X main.date=${DATE}" \
    -o bin/pravah ./cmd/pravah
)

echo "=== CLI: verify binary ==="
"$CLI/bin/pravah" version

if [[ "${SKIP_GORELEASER_CHECK:-}" != "1" ]]; then
  if ! command -v goreleaser >/dev/null 2>&1; then
    echo "error: goreleaser not on PATH (required unless SKIP_GORELEASER_CHECK=1)." >&2
    echo "  https://goreleaser.com/install/" >&2
    exit 1
  fi
  echo "=== CLI: goreleaser check ==="
  (cd "$CLI" && goreleaser check)
fi

echo "✅ CLI checks passed"

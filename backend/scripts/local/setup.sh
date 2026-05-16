#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPO_ROOT="$(cd "$ROOT/.." && pwd)"

mkdir -p "$ROOT/.local/logs" "$ROOT/.local/pids"

if [[ ! -f "$ROOT/.env" ]]; then
  cp "$ROOT/env.local.example" "$ROOT/.env"
  echo "Created backend/.env from env.local.example"
else
  echo "backend/.env already exists (unchanged)"
fi

if [[ ! -f "$REPO_ROOT/web/.env.local" ]]; then
  cp "$REPO_ROOT/web/env.local.example" "$REPO_ROOT/web/.env.local"
  echo "Created web/.env.local from env.local.example"
else
  echo "web/.env.local already exists (unchanged)"
fi

echo ""
echo "If backend/.env was just created, set DB_USERNAME and DB_PASSWORD to match"
echo "  POSTGRES_* in backend/docker-compose.yml (or leave empty for Spring defaults)."
echo ""
echo "Local env ready. Next:"
echo "  cd backend && make local-up        # Docker infra + health wait"
echo "  cd backend && make local-services  # tenant, pipeline, execution, gateway"
echo "  cd backend && make local-dev-token # mint JWT for the UI"
echo "  cd web && npm run dev              # or: make -C backend local-web"

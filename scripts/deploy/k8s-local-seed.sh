#!/usr/bin/env bash
# Seed demo tenant/workflow after Helm install (requires kubectl + postgres port-forward).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAMESPACE="${1:-pravah}"
POSTGRES_SVC="${POSTGRES_SVC:-pravah-postgres}"
SEED_SQL="$ROOT/backend/scripts/local/seed-demo.sql"

if [[ ! -f "$SEED_SQL" ]]; then
  echo "Missing $SEED_SQL" >&2
  exit 1
fi

echo "Waiting for tenant-service pod..."
kubectl -n "$NAMESPACE" wait --for=condition=ready pod \
  -l app.kubernetes.io/component=tenant-service --timeout=300s

echo "Port-forwarding Postgres (background)..."
kubectl -n "$NAMESPACE" port-forward "svc/$POSTGRES_SVC" 15432:5432 &
PF_PID=$!
trap 'kill $PF_PID 2>/dev/null || true' EXIT
sleep 2

export PGPASSWORD="${POSTGRES_PASSWORD:-pravah}"
until psql -h 127.0.0.1 -p 15432 -U pravah -d pravah -c 'SELECT 1' >/dev/null 2>&1; do
  sleep 1
done

echo "Applying seed SQL..."
psql -h 127.0.0.1 -p 15432 -U pravah -d pravah -f "$SEED_SQL"

echo "Seed complete. Sign in: dev@localhost.pravah / PravahDev1!"

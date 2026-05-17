#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"

load_env

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${DB_USERNAME:-pravah}"
if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "Set DB_PASSWORD in backend/.env (match POSTGRES_PASSWORD in docker-compose.yml)." >&2
  exit 1
fi
export PGPASSWORD="${DB_PASSWORD}"

psql_exec() {
  if command -v psql >/dev/null 2>&1; then
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 "$@"
  else
    docker exec -i pravah-postgres psql -U "$PGUSER" -v ON_ERROR_STOP=1 "$@"
  fi
}

assert_migrations_applied() {
  local missing=()
  if ! psql_exec -d pravah_pipeline -tAc "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='connections'" | grep -q 1; then
    missing+=("pravah_pipeline.connections (start pipeline-service)")
  fi
  if ! psql_exec -d pravah_execution -tAc "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='jobs'" | grep -q 1; then
    missing+=("pravah_execution.jobs (start execution-service)")
  fi
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Database schema is not migrated yet. Flyway runs when Java services boot." >&2
    echo "  1. make local-services   # wait ~30–60s for tenant, pipeline, execution, …" >&2
    echo "  2. make local-seed" >&2
    echo "Missing: ${missing[*]}" >&2
    return 1
  fi
  return 0
}

run_psql() {
  if command -v psql >/dev/null 2>&1; then
    if ! (echo >/dev/tcp/"$PGHOST"/"$PGPORT") 2>/dev/null; then
      echo "Postgres is not reachable at $PGHOST:$PGPORT — run: make local-up" >&2
      return 1
    fi
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/local/seed-demo.sql"
    return $?
  fi
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'pravah-postgres'; then
    echo "Using docker exec (psql not installed locally)…"
    docker exec -i pravah-postgres psql -U "$PGUSER" -v ON_ERROR_STOP=1 <"$ROOT/scripts/local/seed-demo.sql"
    return $?
  fi
  echo "Need psql or running container pravah-postgres — run: make local-up" >&2
  return 1
}

echo "Seeding demo data (tenant, pipeline, execution DBs)…"
assert_migrations_applied
run_psql

echo ""
echo "Demo data loaded."
echo "  Project:  ${PRAVAH_DEV_PROJECT_ID:-33333333-3333-4333-8333-333333333333}"
echo "  Workflows: Daily ETL, Event ingestion, Weekly reports (draft)"
echo "  Runs:      5 executions (failed, running, succeeded, pending, cancelled)"
echo "  Job logs:  seeded for failed / running / succeeded / cancelled stages"
echo ""
echo "Try run detail:  http://localhost:5173/app/runs/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001"
echo "Sign in:         dev@localhost.pravah / PravahDev1!  (or make local-dev-token)"

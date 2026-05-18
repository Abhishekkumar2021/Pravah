#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"
load_env 2>/dev/null || true

PRAVAH_LOCAL_PID_DIR="${PRAVAH_LOCAL_PID_DIR:-$ROOT/.local/pids}"

check_port() {
  local label=$1
  local port=$2
  if (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
    echo "  OK  $label (:$port)"
  else
    echo "  --  $label (:$port)"
  fi
}

echo "Infrastructure:"
check_port "PostgreSQL" 5432
check_port "Kafka" 29092
check_port "Redis" 6379

echo ""
echo "Java services (pid files):"
for svc in tenant-service pipeline-service execution-service scheduler-service notification-service gateway; do
  pidfile="$PRAVAH_LOCAL_PID_DIR/${svc}.pid"
  if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "  OK  $svc (pid $(cat "$pidfile"))"
  else
    echo "  --  $svc"
  fi
done

echo ""
check_port "Gateway HTTP" 8080
check_port "Tenant" 8082
check_port "Pipeline" 8083
check_port "Execution" 8084
check_port "Scheduler" 8085
check_port "Notification" 8088

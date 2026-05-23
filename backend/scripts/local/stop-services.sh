#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh" 2>/dev/null || true

PRAVAH_LOCAL_PID_DIR="${PRAVAH_LOCAL_PID_DIR:-$ROOT/.local/pids}"

stop_pidfile() {
  local name=$1
  local pidfile="$PRAVAH_LOCAL_PID_DIR/${name}.pid"
  if [[ ! -f "$pidfile" ]]; then
    return 0
  fi
  local pid
  pid="$(cat "$pidfile")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping $name wrapper (pid $pid)"
    kill "$pid" 2>/dev/null || true
    pkill -P "$pid" 2>/dev/null || true
  fi
  rm -f "$pidfile"
}

kill_port() {
  local label=$1
  local port=$2
  local pids
  pids="$(lsof -ti :"$port" 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "Stopping $label on port $port (pid(s) $pids)"
    # shellcheck disable=SC2086
    kill $pids 2>/dev/null || true
    sleep 1
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
  fi
}

for svc in gateway agent-service metadata-service runner-service connect-service \
  notification-service scheduler-service execution-service pipeline-service tenant-service; do
  stop_pidfile "$svc"
done

# Gradle bootRun leaves the JVM listening even when the wrapper pid exits.
kill_port "gateway" 8080
kill_port "agent-service" 8089
kill_port "metadata-service" 8087
kill_port "runner-service" 8086
kill_port "connect-service" 8091
kill_port "notification-service" 8088
kill_port "tenant-service" 8082
kill_port "scheduler-service" 8085
kill_port "pipeline-service" 8083
kill_port "execution-service" 8084

echo "Local Java services stopped."

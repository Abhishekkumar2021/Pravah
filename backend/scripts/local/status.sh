#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"
load_env 2>/dev/null || true

PRAVAH_LOCAL_PID_DIR="${PRAVAH_LOCAL_PID_DIR:-$ROOT/.local/pids}"
PRAVAH_LOCAL_LOG_DIR="${PRAVAH_LOCAL_LOG_DIR:-$ROOT/.local/logs}"

check_port() {
  local label=$1
  local port=$2
  if (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
    echo "  OK  $label (:$port)"
    return 0
  fi
  echo "  --  $label (:$port)"
  return 1
}

check_service() {
  local name=$1
  local port=$2
  local pidfile="$PRAVAH_LOCAL_PID_DIR/${name}.pid"
  local logfile="$PRAVAH_LOCAL_LOG_DIR/${name}.log"
  local port_up=false
  local wrapper_up=false

  if (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
    port_up=true
  fi

  if [[ -f "$pidfile" ]]; then
    local pid
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      wrapper_up=true
    fi
  fi

  if $port_up; then
    if $wrapper_up; then
      echo "  OK  $name (pid $(cat "$pidfile"), :$port)"
    else
      echo "  OK  $name (:$port)"
    fi
    return 0
  fi

  if $wrapper_up; then
    echo "  !!  $name (gradle still running, :$port down — still starting or failed; see $logfile)"
    return 1
  fi

  if [[ -f "$pidfile" ]]; then
    echo "  --  $name (stale pid file; run: make local-services-stop && make local-services)"
    return 1
  fi

  echo "  --  $name"
  return 1
}

echo "Infrastructure:"
check_port "PostgreSQL" 5432 || true
check_port "Kafka" 29092 || true
check_port "Redis" 6379 || true

echo ""
echo "Java services:"
check_service "tenant-service" 8082 || true
check_service "pipeline-service" 8083 || true
check_service "execution-service" 8084 || true
check_service "scheduler-service" 8085 || true
check_service "notification-service" 8088 || true
check_service "gateway" 8080 || true

echo ""
echo "Tip: first boot takes ~30–60s. If a service shows !!, tail its log:"
echo "  tail -f $PRAVAH_LOCAL_LOG_DIR/<service>.log"

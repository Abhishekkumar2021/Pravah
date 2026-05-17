#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"
load_env

mkdir -p "$PRAVAH_LOCAL_LOG_DIR" "$PRAVAH_LOCAL_PID_DIR"
cd "$ROOT"

start_one() {
  local name=$1
  local gradle_path=$2
  local logfile="$PRAVAH_LOCAL_LOG_DIR/${name}.log"
  local pidfile="$PRAVAH_LOCAL_PID_DIR/${name}.pid"

  if [[ -f "$pidfile" ]]; then
    local old_pid
    old_pid="$(cat "$pidfile")"
    if kill -0 "$old_pid" 2>/dev/null; then
      echo "$name already running (pid $old_pid)"
      return 0
    fi
    rm -f "$pidfile"
  fi

  echo "Starting $name → $logfile"
  # Ensure backend/.env vars reach the Spring Boot process (profile, Kafka, JWKS, …).
  nohup env \
    SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}" \
    KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:29092}" \
    PRAVAH_JWKS_URL="${PRAVAH_JWKS_URL:-http://localhost:8082/.well-known/jwks.json}" \
    PRAVAH_JWT_ISSUER="${PRAVAH_JWT_ISSUER:-pravah-dev}" \
    PRAVAH_REALTIME_REDIS_ENABLED="${PRAVAH_REALTIME_REDIS_ENABLED:-true}" \
    PRAVAH_WS_ALLOWED_ORIGINS="${PRAVAH_WS_ALLOWED_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173}" \
    PIPELINE_SERVICE_BASE_URL="${PIPELINE_SERVICE_BASE_URL:-http://localhost:8083}" \
    EXECUTION_SERVICE_BASE_URL="${EXECUTION_SERVICE_BASE_URL:-http://localhost:8084}" \
    PRAVAH_INTERNAL_SERVICE_SECRET="${PRAVAH_INTERNAL_SERVICE_SECRET:-pravah-local-internal-secret}" \
  ./gradlew "$gradle_path:bootRun" --no-daemon >"$logfile" 2>&1 &
  echo $! >"$pidfile"
}

# Boot order: JWKS issuer first, then consumers, gateway last.
start_one tenant-service :services:tenant-service
sleep 3
start_one pipeline-service :services:pipeline-service
start_one execution-service :services:execution-service
start_one scheduler-service :services:scheduler-service
sleep 3
start_one gateway :services:gateway

echo ""
echo "Services starting in background. Logs: $PRAVAH_LOCAL_LOG_DIR"
echo "Wait ~30–60s, then:"
echo "  make local-status"
echo "  make local-seed          # after services are up (~60s); Flyway must run before seed"
echo "  make local-dev-token     # or sign in at http://localhost:5173/login"
echo ""
echo "After pulling auth changes, run: make local-services-stop && make local-services"

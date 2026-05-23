#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"
load_env

mkdir -p "$PRAVAH_LOCAL_LOG_DIR" "$PRAVAH_LOCAL_PID_DIR"
cd "$ROOT"

wait_for_port() {
  local port=$1
  local label=$2
  local max_wait=${3:-120}
  local elapsed=0
  while (( elapsed < max_wait )); do
    if (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
      echo "$label listening on :$port (${elapsed}s)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "WARN: $label did not open :$port within ${max_wait}s — check $PRAVAH_LOCAL_LOG_DIR/${label}.log" >&2
  return 1
}

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
    RUNNER_SERVICE_BASE_URL="${RUNNER_SERVICE_BASE_URL:-http://localhost:8086}" \
    PRAVAH_INTERNAL_SERVICE_SECRET="${PRAVAH_INTERNAL_SERVICE_SECRET:-pravah-local-internal-secret}" \
    PRAVAH_RUNNER_BOOTSTRAP_SECRET="${PRAVAH_RUNNER_BOOTSTRAP_SECRET:-${PRAVAH_INTERNAL_SERVICE_SECRET:-pravah-local-internal-secret}}" \
  ./gradlew "$gradle_path:bootRun" --no-daemon >"$logfile" 2>&1 &
  echo $! >"$pidfile"
}

# One Gradle invocation avoids parallel bootRun fighting over buildLogic.lock.
echo "Pre-compiling service modules (single Gradle process)..."
./gradlew \
  :services:tenant-service:classes \
  :services:pipeline-service:classes \
  :services:execution-service:classes \
  :services:scheduler-service:classes \
  :services:notification-service:classes \
  :services:connect-service:classes \
  :services:runner-service:classes \
  :services:metadata-service:classes \
  :services:agent-service:classes \
  :services:gateway:classes \
  --no-daemon -q

# Boot order: JWKS issuer first, then consumers, gateway last. Stagger bootRun to avoid Gradle lock timeouts.
start_one tenant-service :services:tenant-service
wait_for_port 8082 tenant-service 90 || true
start_one pipeline-service :services:pipeline-service
sleep 8
start_one execution-service :services:execution-service
sleep 8
start_one scheduler-service :services:scheduler-service
sleep 8
start_one notification-service :services:notification-service
sleep 8
start_one connect-service :services:connect-service
sleep 8
start_one runner-service :services:runner-service
sleep 8
start_one metadata-service :services:metadata-service
sleep 8
start_one agent-service :services:agent-service
sleep 8
start_one gateway :services:gateway

echo ""
echo "Services starting in background. Logs: $PRAVAH_LOCAL_LOG_DIR"
echo "Wait ~30-60 seconds, then:"
echo "  make local-status"
echo "  make local-seed          # after services are up (~60s); Flyway must run before seed"
echo "  http://localhost:5173/login  # sign in (after local-seed)"


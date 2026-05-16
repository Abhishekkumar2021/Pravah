# shellcheck shell=bash
# Shared helpers for local stack scripts. Source from backend/:  source scripts/local/lib.sh

_pravah_backend_root() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  echo "$here"
}

load_env() {
  local root
  root="$(_pravah_backend_root)"
  if [[ -f "$root/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$root/.env"
    set +a
  else
    echo "Missing $root/.env — run: make local-setup" >&2
    return 1
  fi
  PRAVAH_LOCAL_DIR="${PRAVAH_LOCAL_DIR:-$root/.local}"
  PRAVAH_LOCAL_LOG_DIR="${PRAVAH_LOCAL_LOG_DIR:-$PRAVAH_LOCAL_DIR/logs}"
  PRAVAH_LOCAL_PID_DIR="${PRAVAH_LOCAL_PID_DIR:-$PRAVAH_LOCAL_DIR/pids}"
  export PRAVAH_LOCAL_DIR PRAVAH_LOCAL_LOG_DIR PRAVAH_LOCAL_PID_DIR
}

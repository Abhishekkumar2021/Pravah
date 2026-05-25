#!/usr/bin/env bash
set -euo pipefail

wait_port() {
  local name=$1
  local host=$2
  local port=$3
  local max=${4:-60}
  local i=0
  while ! (echo >/dev/tcp/"$host"/"$port") 2>/dev/null; do
    i=$((i + 1))
    if [[ $i -ge $max ]]; then
      echo "Timed out waiting for $name at $host:$port" >&2
      return 1
    fi
    sleep 1
  done
  echo "$name is up ($host:$port)"
}

echo "Waiting for Docker infrastructure..."
wait_port "PostgreSQL" 127.0.0.1 5432 90
wait_port "Kafka" 127.0.0.1 29092 120
wait_port "Redis" 127.0.0.1 6379 60
echo "Infrastructure ready."

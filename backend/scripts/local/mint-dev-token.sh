#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/local/lib.sh
source "$ROOT/scripts/local/lib.sh"
load_env

GATEWAY="${PRAVAH_GATEWAY_URL:-http://localhost:8080}"
TENANT_ID="${PRAVAH_DEV_TENANT_ID:-11111111-1111-4111-8111-111111111111}"
USER_ID="${PRAVAH_DEV_USER_ID:-22222222-2222-4222-8222-222222222222}"

body=$(printf '{"userId":"%s","tenantId":"%s"}' "$USER_ID" "$TENANT_ID")

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

curl_args=(
  -sS
  -w "\n%{http_code}"
  -X POST "$GATEWAY/api/v1/auth/dev-token"
  -H "Content-Type: application/json"
)
if [[ -n "${PRAVAH_DEV_TOKEN_SECRET:-}" ]]; then
  curl_args+=(-H "X-Pravah-Dev-Secret: $PRAVAH_DEV_TOKEN_SECRET")
fi
curl_args+=(-d "$body")

response="$(curl "${curl_args[@]}")" || {
  echo "Failed to reach gateway at $GATEWAY" >&2
  echo "Run: make local-status  (gateway should listen on 8080)" >&2
  exit 1
}

http_code="${response##*$'\n'}"
response_body="${response%$'\n'*}"

if [[ "$http_code" != "200" ]]; then
  echo "Failed to mint dev token (HTTP $http_code)." >&2
  if [[ -n "$response_body" ]]; then
    echo "$response_body" >&2
  fi
  echo "Ensure services were restarted after the latest fix: make local-services-stop && make local-services" >&2
  exit 1
fi

response="$response_body"

if command -v jq >/dev/null 2>&1; then
  token="$(echo "$response" | jq -r '.accessToken')"
else
  token="$(echo "$response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
fi

if [[ -z "$token" || "$token" == "null" ]]; then
  echo "Unexpected response: $response" >&2
  exit 1
fi

echo "$token"

#!/usr/bin/env bash
# Initialize per-service databases on RDS after Terraform apply (pathway #10).
#
# Requires: psql, terraform output from deploy/terraform/aws/reference
#
# Usage:
#   cd deploy/terraform/aws/reference
#   ../../scripts/deploy/terraform-init-rds.sh
#
# Env:
#   TF_DIR          Terraform root (default: deploy/terraform/aws/reference)
#   POSTGRES_HOST   Override RDS endpoint
#   POSTGRES_USER   Override username (default: pravah)
#   POSTGRES_PASSWORD Override password (reads from terraform output if unset)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TF_DIR="${TF_DIR:-$ROOT/deploy/terraform/aws/reference}"
INIT_SQL="$ROOT/deploy/helm/pravah-platform/files/postgres-init.sql"

log() { echo "[terraform-init-rds] $*"; }
fail() { echo "[terraform-init-rds] ERROR: $*" >&2; exit 1; }

command -v psql >/dev/null 2>&1 || fail "psql is required"
command -v terraform >/dev/null 2>&1 || fail "terraform is required"
[[ -f "$INIT_SQL" ]] || fail "Missing init SQL: $INIT_SQL"

cd "$TF_DIR"

HOST="${POSTGRES_HOST:-$(terraform output -raw postgres_host)}"
USER="${POSTGRES_USER:-$(terraform output -raw postgres_username)}"
PASS="${POSTGRES_PASSWORD:-$(terraform output -raw postgres_password)}"

log "Applying postgres-init.sql to ${HOST} as ${USER}..."
PGPASSWORD="$PASS" psql "host=${HOST} port=5432 user=${USER} dbname=pravah sslmode=require" -f "$INIT_SQL"

log "OK — per-service databases created."

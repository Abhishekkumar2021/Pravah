#!/usr/bin/env bash
# Offline Terraform validation (fmt + init + validate). No AWS credentials required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STACK="$ROOT/deploy/terraform/aws/reference"

command -v terraform >/dev/null 2>&1 || {
  echo "ERROR: terraform CLI not found" >&2
  exit 1
}

echo "Terraform fmt check..."
terraform -chdir="$STACK" fmt -check -recursive "$ROOT/deploy/terraform"

echo "Terraform init (no backend)..."
terraform -chdir="$STACK" init -backend=false -input=false

echo "Terraform validate..."
terraform -chdir="$STACK" validate

echo "OK — Terraform manifests valid"

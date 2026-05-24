#!/usr/bin/env bash
# Print Helm --set flags from Terraform outputs (pathway #10).
#
# Usage (from repo root or TF_DIR):
#   ./scripts/deploy/terraform-helm-bridge.sh
#
# Env:
#   TF_DIR  Terraform root (default: deploy/terraform/aws/reference)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TF_DIR="${TF_DIR:-$ROOT/deploy/terraform/aws/reference}"

command -v terraform >/dev/null 2>&1 || {
  echo "ERROR: terraform CLI not found" >&2
  exit 1
}

cd "$TF_DIR"

echo "# Helm external service flags (paste after helm upgrade --install ...)"
terraform output -raw helm_values_snippet
echo ""
echo "# IRSA role ARN (also embedded above when using reference stack)"
terraform output -raw artifacts_irsa_role_arn 2>/dev/null || true

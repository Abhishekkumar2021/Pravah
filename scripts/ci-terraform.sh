#!/usr/bin/env bash
# Terraform CI parity (fmt + init + validate). Matches .github/workflows/terraform-ci.yml.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/scripts/deploy/validate-terraform.sh"

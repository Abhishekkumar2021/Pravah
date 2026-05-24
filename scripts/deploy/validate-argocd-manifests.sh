#!/usr/bin/env bash
# Validate Argo CD manifest YAML structure (no cluster required).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

validate_yaml_file() {
  local file="$1"
  if command -v python3 >/dev/null 2>&1 && python3 -c "import yaml" 2>/dev/null; then
    python3 - "$file" <<'PY'
import sys
import yaml

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    docs = [doc for doc in yaml.safe_load_all(handle) if doc is not None]
if not docs:
    raise SystemExit(f"{path}: no Kubernetes documents found")
for doc in docs:
    if not isinstance(doc, dict) or not doc.get("kind") or not doc.get("apiVersion"):
        raise SystemExit(f"{path}: missing kind or apiVersion")
print(f"OK  {path} ({doc['kind']})")
PY
  elif command -v ruby >/dev/null 2>&1; then
    ruby -ryaml - "$file" <<'RUBY'
path = ARGV[0]
docs = YAML.load_stream(File.read(path)).compact
abort "#{path}: no Kubernetes documents found" if docs.empty?
docs.each do |doc|
  abort "#{path}: missing kind or apiVersion" unless doc.is_a?(Hash) && doc["kind"] && doc["apiVersion"]
  puts "OK  #{path} (#{doc['kind']})"
end
RUBY
  else
    echo "ERROR: need python3+PyYAML or ruby for YAML validation" >&2
    exit 1
  fi
}

echo "Validating Argo CD manifests..."
validate_yaml_file "$ROOT/deploy/argocd/appproject-pravah.yaml"
validate_yaml_file "$ROOT/deploy/argocd/applications/pravah-platform-local.yaml"
validate_yaml_file "$ROOT/deploy/argocd/applications/pravah-platform-production.yaml"
validate_yaml_file "$ROOT/deploy/argocd/applications/pravah-platform-production.yaml.example"

if command -v kubectl >/dev/null 2>&1 && kubectl cluster-info >/dev/null 2>&1; then
  echo "Cluster reachable — running kubectl client dry-run..."
  kubectl apply --dry-run=client --validate=false \
    -f "$ROOT/deploy/argocd/appproject-pravah.yaml" \
    -f "$ROOT/deploy/argocd/applications/pravah-platform-local.yaml" \
    -f "$ROOT/deploy/argocd/applications/pravah-platform-production.yaml" \
    -f "$ROOT/deploy/argocd/applications/pravah-platform-production.yaml.example"
fi

echo "OK — Argo CD manifests valid"

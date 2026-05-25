# Vault policy for pipeline-service Kubernetes auth role (ADR-007).
# Grant KV read for connection refs and Transit encrypt/decrypt for tenant-{uuid} keys.
path "secret/data/*" {
  capabilities = ["read"]
}

path "transit/encrypt/tenant-*" {
  capabilities = ["create", "update"]
}

path "transit/decrypt/tenant-*" {
  capabilities = ["update"]
}

path "transit/keys/tenant-*" {
  capabilities = ["read"]
}

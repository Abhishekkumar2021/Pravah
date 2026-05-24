-- Vault Transit ciphertext for tenant-stored secrets (ADR-007, pathway #8)
ALTER TABLE tenant_secrets
    ADD COLUMN encrypted_value TEXT;

ALTER TABLE tenant_secrets
    ALTER COLUMN provider_path DROP NOT NULL;

COMMENT ON COLUMN tenant_secrets.encrypted_value IS
    'Vault Transit ciphertext when provider=transit; plaintext never stored';

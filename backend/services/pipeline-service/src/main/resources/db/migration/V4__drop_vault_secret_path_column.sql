-- Move vault_secret_path into config JSONB (Kubernetes-style credential references)
-- config.credentials.password: "env:VAR_NAME" | future: "vault:path#key"

UPDATE connections
SET config = jsonb_set(
    config,
    '{credentials,password}',
    to_jsonb(vault_secret_path),
    true)
WHERE vault_secret_path IS NOT NULL
  AND vault_secret_path <> ''
  AND (config -> 'credentials' ->> 'password') IS NULL;

ALTER TABLE connections DROP COLUMN IF EXISTS vault_secret_path;

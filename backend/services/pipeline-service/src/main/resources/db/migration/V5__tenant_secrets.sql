-- Tenant-scoped secret references for ${secret.name} resolution (docs/lld/07-value-resolution.md)
-- Actual secret values stored in external providers (env, Vault, AWS SM)

CREATE TABLE tenant_secrets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    provider        VARCHAR(50) NOT NULL,   -- 'env', 'vault', 'aws_sm'
    provider_path   VARCHAR(500) NOT NULL,  -- provider-specific path/reference
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_tenant_secrets_tenant ON tenant_secrets(tenant_id);
CREATE INDEX idx_tenant_secrets_tenant_name ON tenant_secrets(tenant_id, name);

ALTER TABLE tenant_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_secrets FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_secrets ON tenant_secrets FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

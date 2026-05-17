-- Named connections to external systems (docs/lld/02-database-erd.md)

CREATE TABLE connections (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    type                VARCHAR(100) NOT NULL,
    config              JSONB NOT NULL,
    vault_secret_path   VARCHAR(500),
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_connections_tenant ON connections(tenant_id, name);

ALTER TABLE connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE connections FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_connections ON connections FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

-- Tenant row access: authenticated requests may only read/update their own tenant row.

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_tenants ON tenants FOR ALL
    USING (id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (id = current_setting('pravah.current_tenant_id', true)::UUID);

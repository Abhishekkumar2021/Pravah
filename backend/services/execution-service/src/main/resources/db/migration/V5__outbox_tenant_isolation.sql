-- V5: Add tenant_id to outbox/processed_events for observability and future RLS (code review remediation)
-- Note: RLS is not enforced on outbox because the relay runs without tenant context intentionally.
-- tenant_id is added for operational queries (e.g., "show failed events for tenant X").

ALTER TABLE outbox ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS tenant_id UUID;

CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON outbox(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_processed_events_tenant ON processed_events(tenant_id) WHERE tenant_id IS NOT NULL;

COMMENT ON COLUMN outbox.tenant_id IS 'Tenant ID for operational queries; not RLS-enforced (relay is cross-tenant)';
COMMENT ON COLUMN processed_events.tenant_id IS 'Tenant ID for operational queries; not RLS-enforced';

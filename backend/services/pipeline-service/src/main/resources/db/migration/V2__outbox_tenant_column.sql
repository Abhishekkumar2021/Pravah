-- V2: Add tenant_id and retry_count to outbox for observability and dead-letter handling (code review remediation)
-- Note: RLS is not enforced on outbox because the relay runs without tenant context intentionally.
-- tenant_id is added for operational queries (e.g., "show failed events for tenant X").

ALTER TABLE outbox ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON outbox(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_failed ON outbox(retry_count) WHERE retry_count > 0 AND published_at IS NULL;

COMMENT ON COLUMN outbox.tenant_id IS 'Tenant ID for operational queries; not RLS-enforced (relay is cross-tenant)';
COMMENT ON COLUMN outbox.retry_count IS 'Number of failed publish attempts; used for dead-letter handling';

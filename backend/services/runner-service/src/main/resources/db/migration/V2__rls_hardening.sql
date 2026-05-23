-- Harden runner RLS: FORCE, WITH CHECK, job_assignments tenant isolation, maintenance bypass

DROP POLICY IF EXISTS runners_tenant_isolation ON runners;
DROP POLICY IF EXISTS runner_labels_tenant_isolation ON runner_labels;

CREATE POLICY runners_tenant_isolation ON runners
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid);

CREATE POLICY runner_labels_tenant_isolation ON runner_labels
    USING (runner_id IN (
        SELECT id FROM runners
        WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::uuid))
    WITH CHECK (runner_id IN (
        SELECT id FROM runners
        WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::uuid));

ALTER TABLE runners FORCE ROW LEVEL SECURITY;
ALTER TABLE runner_labels FORCE ROW LEVEL SECURITY;

-- job_assignments: add tenant_id for RLS
ALTER TABLE job_assignments ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE job_assignments ja
SET tenant_id = r.tenant_id
FROM runners r
WHERE ja.runner_id = r.id AND ja.tenant_id IS NULL;

ALTER TABLE job_assignments ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_job_assignments_tenant_id ON job_assignments(tenant_id);

ALTER TABLE job_assignments ENABLE ROW LEVEL SECURITY;

CREATE POLICY job_assignments_tenant_isolation ON job_assignments
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::uuid);

ALTER TABLE job_assignments FORCE ROW LEVEL SECURITY;

-- Cross-tenant maintenance (stale runner detection, etc.)
CREATE POLICY runners_system_maintenance ON runners
    FOR ALL
    USING (current_setting('pravah.system_maintenance_enabled', true) = 'true')
    WITH CHECK (current_setting('pravah.system_maintenance_enabled', true) = 'true');

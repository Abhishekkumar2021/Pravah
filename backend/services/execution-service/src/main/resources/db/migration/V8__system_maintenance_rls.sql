-- RLS bypass for cross-tenant scheduled maintenance (timeouts, artifact cleanup)

CREATE POLICY executions_system_maintenance ON executions
    FOR SELECT
    USING (current_setting('pravah.system_maintenance_enabled', true) = 'true');

CREATE POLICY jobs_system_maintenance ON jobs
    FOR SELECT
    USING (current_setting('pravah.system_maintenance_enabled', true) = 'true');

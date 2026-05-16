-- V2: RLS bypass for cross-tenant schedule evaluation (leader job only)

CREATE POLICY scheduler_evaluation ON schedules
    FOR ALL
    USING (current_setting('pravah.scheduler_evaluation_enabled', true) = 'true')
    WITH CHECK (current_setting('pravah.scheduler_evaluation_enabled', true) = 'true');

CREATE POLICY scheduler_evaluation_history ON schedule_history
    FOR ALL
    USING (current_setting('pravah.scheduler_evaluation_enabled', true) = 'true')
    WITH CHECK (current_setting('pravah.scheduler_evaluation_enabled', true) = 'true');

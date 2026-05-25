-- Cross-tenant read for leader trigger dispatch retry job (no HTTP tenant context on relay tick).

CREATE POLICY trigger_dispatch_relay_pending ON trigger_dispatch_pending FOR ALL
    USING (current_setting('pravah.trigger_dispatch_relay_enabled', true) = 'true')
    WITH CHECK (current_setting('pravah.trigger_dispatch_relay_enabled', true) = 'true');

CREATE POLICY trigger_dispatch_relay_history ON trigger_dispatch_history FOR ALL
    USING (current_setting('pravah.trigger_dispatch_relay_enabled', true) = 'true')
    WITH CHECK (current_setting('pravah.trigger_dispatch_relay_enabled', true) = 'true');

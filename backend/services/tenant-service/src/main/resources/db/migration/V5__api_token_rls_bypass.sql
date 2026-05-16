-- V5: API tokens RLS bypass and schema additions (US-10.08)
--
-- API token authentication must resolve tenant/user by token hash before tenant context exists.
-- Same pattern as V4 auth email lookup: transaction-scoped flag, SELECT only.

-- Add version column for optimistic locking
ALTER TABLE api_tokens ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE POLICY auth_api_token_lookup ON api_tokens
    FOR SELECT
    USING (
        current_setting('pravah.api_token_lookup_enabled', true) = 'true'
    );

-- Grant admins API token management (owner already has "*")
UPDATE roles
SET permissions = '["pipelines:*", "executions:*", "users:*", "settings:read", "api_tokens:*"]'
WHERE id = '00000000-0000-0000-0000-000000000002';

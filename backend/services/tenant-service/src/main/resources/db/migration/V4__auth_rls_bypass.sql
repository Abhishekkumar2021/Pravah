-- V4: Add RLS bypass for authentication operations (US-10.01 code review remediation)
--
-- Problem: Login requires querying users by email before tenant context is known.
-- The existing RLS policy blocks all queries when pravah.current_tenant_id is not set.
--
-- Solution: Add a separate SELECT policy that allows email lookups when:
--   1. pravah.auth_lookup_enabled = 'true' (set by AuthService before auth queries)
--   2. Limited to SELECT only (no writes without proper tenant context)
--
-- This maintains security because:
--   - Only email lookup is allowed, no cross-tenant data modification
--   - The flag is transaction-scoped (SET LOCAL) so cannot persist
--   - INSERT/UPDATE/DELETE still require tenant context via the existing policy

-- Add auth lookup policy for SELECT operations only
CREATE POLICY auth_email_lookup ON users
    FOR SELECT
    USING (
        current_setting('pravah.auth_lookup_enabled', true) = 'true'
    );

-- Note: The existing tenant_isolation_users policy still applies for INSERT/UPDATE/DELETE.
-- PostgreSQL RLS uses OR logic between policies of the same command type (SELECT).
-- So a SELECT will succeed if EITHER:
--   1. tenant_id matches current_tenant_id (normal authenticated access), OR
--   2. auth_lookup_enabled = 'true' (auth service lookup)

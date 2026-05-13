-- ============================================================================
-- V1: Tenant Service Schema
-- Creates core tenant management tables with Row-Level Security
-- 
-- Naming Convention: Use "tenant" consistently (not "org" or "organization")
-- Per ADR-006 and ADR-013, tenant_id is the isolation boundary.
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- Custom ENUM Types
-- Using PostgreSQL ENUMs for type safety and query optimization
-- Values are UPPERCASE to match Java enum convention
-- ============================================================================
CREATE TYPE tenant_tier AS ENUM ('FREE', 'TEAM', 'ENTERPRISE');
CREATE TYPE user_status AS ENUM ('PENDING', 'ACTIVE', 'INACTIVE', 'LOCKED');

-- ============================================================================
-- Tenants (top-level isolation boundary)
-- ============================================================================
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    tier            tenant_tier NOT NULL DEFAULT 'FREE',
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,98}[a-z0-9])?$')
);

COMMENT ON TABLE tenants IS 'Top-level tenant entity. All resources belong to a tenant.';
COMMENT ON COLUMN tenants.slug IS 'URL-friendly unique identifier (lowercase alphanumeric with hyphens)';
COMMENT ON COLUMN tenants.tier IS 'Subscription tier: FREE, TEAM, ENTERPRISE';

-- ============================================================================
-- Users
-- ============================================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),
    mfa_secret      VARCHAR(255),
    status          user_status NOT NULL DEFAULT 'PENDING',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

COMMENT ON TABLE users IS 'User accounts within a tenant';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password. NULL for OAuth-only users.';
COMMENT ON COLUMN users.failed_login_attempts IS 'Counter for account lockout after failed attempts';
COMMENT ON COLUMN users.locked_until IS 'Account locked until this time (for failed login protection)';

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

-- ============================================================================
-- Roles (system + custom)
-- ============================================================================
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    permissions     JSONB NOT NULL DEFAULT '[]',
    is_system       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);

COMMENT ON TABLE roles IS 'Role definitions. System roles have tenant_id=NULL and is_system=true';
COMMENT ON COLUMN roles.permissions IS 'Array of permission strings, e.g. ["pipelines:read", "pipelines:write"]';

-- System roles (tenant_id = NULL, shared across all tenants)
INSERT INTO roles (id, tenant_id, name, description, permissions, is_system) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'owner', 'Full access to tenant', '["*"]', true),
    ('00000000-0000-0000-0000-000000000002', NULL, 'admin', 'Administrative access', '["pipelines:*", "executions:*", "users:*", "settings:read"]', true),
    ('00000000-0000-0000-0000-000000000003', NULL, 'editor', 'Can create and modify pipelines', '["pipelines:read", "pipelines:write", "executions:*"]', true),
    ('00000000-0000-0000-0000-000000000004', NULL, 'viewer', 'Read-only access', '["pipelines:read", "executions:read"]', true);

-- ============================================================================
-- Tenant Members (user-role assignment at tenant level)
-- ============================================================================
CREATE TABLE tenant_members (
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    PRIMARY KEY (tenant_id, user_id)
);

COMMENT ON TABLE tenant_members IS 'Maps users to their roles within a tenant';

CREATE INDEX idx_tenant_members_user ON tenant_members(user_id);

-- ============================================================================
-- Teams (optional grouping within a tenant)
-- ============================================================================
CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT uq_teams_tenant_name UNIQUE (tenant_id, name)
);

COMMENT ON TABLE teams IS 'Teams within a tenant for grouping users and projects';

CREATE INDEX idx_teams_tenant_id ON teams(tenant_id);

-- ============================================================================
-- Team Members
-- ============================================================================
CREATE TABLE team_members (
    team_id         UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX idx_team_members_user ON team_members(user_id);

-- ============================================================================
-- API Tokens
-- ============================================================================
CREATE TABLE api_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    permissions     JSONB NOT NULL DEFAULT '[]',
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);

COMMENT ON TABLE api_tokens IS 'API tokens for programmatic access';
COMMENT ON COLUMN api_tokens.token_hash IS 'SHA-256 hash of the token. Original token shown only at creation.';

CREATE INDEX idx_api_tokens_tenant ON api_tokens(tenant_id);
CREATE INDEX idx_api_tokens_user ON api_tokens(user_id);
CREATE INDEX idx_api_tokens_hash ON api_tokens(token_hash) WHERE revoked_at IS NULL;

-- ============================================================================
-- Audit Logs (append-only)
-- ============================================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     UUID,
    details         JSONB NOT NULL DEFAULT '{}',
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail of all actions';

CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id, created_at DESC);

-- ============================================================================
-- Transactional Outbox (for event publishing)
-- ============================================================================
CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

COMMENT ON TABLE outbox IS 'Transactional outbox for reliable event publishing to Kafka';

CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- ============================================================================
-- Row-Level Security Policies
-- Per ADR-013: PostgreSQL Row-Level Security for Tenant Data Isolation
-- ============================================================================

-- Enable RLS on tenant-scoped tables
-- FORCE ensures RLS applies even if the application role owns the tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE teams FORCE ROW LEVEL SECURITY;

ALTER TABLE team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_members FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_members FORCE ROW LEVEL SECURITY;

ALTER TABLE api_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_tokens FORCE ROW LEVEL SECURITY;

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;

-- Enable RLS on roles table (custom roles need tenant isolation)
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

-- Create policies for tenant isolation
-- Per ADR-013: current_setting(..., TRUE) returns NULL if not set.
-- NULL = NULL evaluates to FALSE, returning zero rows (safe failure mode).
-- Application sets: SET LOCAL pravah.current_tenant_id = '<uuid>'

CREATE POLICY tenant_isolation_users ON users
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_teams ON teams
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_team_members ON team_members
    FOR ALL
    USING (team_id IN (
        SELECT id FROM teams WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ))
    WITH CHECK (team_id IN (
        SELECT id FROM teams WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ));

CREATE POLICY tenant_isolation_tenant_members ON tenant_members
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_api_tokens ON api_tokens
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_audit_logs ON audit_logs
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

-- Policy for roles: system roles (tenant_id=NULL) are visible to all, custom roles are tenant-scoped
CREATE POLICY tenant_isolation_roles ON roles
    FOR ALL
    USING (
        tenant_id IS NULL OR 
        tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    )
    WITH CHECK (
        tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    );

-- ============================================================================
-- Helper function for updated_at trigger
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

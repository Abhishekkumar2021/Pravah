-- ============================================================================
-- V2: Add Projects, Project Members, and Sessions
-- Per LLD database-erd.md specification
-- ============================================================================

-- ============================================================================
-- Projects (within a team)
-- ============================================================================
CREATE TABLE IF NOT EXISTS projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    team_id         UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT uq_projects_team_name UNIQUE (team_id, name)
);

COMMENT ON TABLE projects IS 'Projects within a team, for organizing pipelines';

CREATE INDEX IF NOT EXISTS idx_projects_team_id ON projects(team_id);
CREATE INDEX IF NOT EXISTS idx_projects_tenant_id ON projects(tenant_id);

-- ============================================================================
-- Project Members
-- ============================================================================
CREATE TABLE IF NOT EXISTS project_members (
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id),
    
    PRIMARY KEY (project_id, user_id)
);

COMMENT ON TABLE project_members IS 'Maps users to their roles within a project';

CREATE INDEX IF NOT EXISTS idx_project_members_user ON project_members(user_id);

-- ============================================================================
-- Sessions (for session management and token revocation)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE sessions IS 'Active user sessions for JWT refresh token tracking';

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

-- ============================================================================
-- Row-Level Security for new tables
-- ============================================================================
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;

ALTER TABLE project_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_members FORCE ROW LEVEL SECURITY;

-- Sessions are per-user but still need tenant isolation via the user's tenant
-- Using a join through users table

-- Projects policy: tenant isolation
CREATE POLICY tenant_isolation_projects ON projects
    FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

-- Project members policy: tenant isolation via project -> team -> tenant
CREATE POLICY tenant_isolation_project_members ON project_members
    FOR ALL
    USING (project_id IN (
        SELECT id FROM projects WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ))
    WITH CHECK (project_id IN (
        SELECT id FROM projects WHERE tenant_id = current_setting('pravah.current_tenant_id', true)::UUID
    ));

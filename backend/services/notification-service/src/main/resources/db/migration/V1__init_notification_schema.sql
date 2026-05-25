-- Notification Service Schema (US-04.04, US-04.07, US-04.08, US-04.19)
-- Covers: alert rules, notification channels, audit log, in-app notifications

-- Alert rules define when and how to notify (per-workflow or global)
CREATE TABLE alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID,  -- NULL = tenant-wide rule
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    
    -- Trigger conditions (JSON for flexibility)
    -- e.g. {"events": ["execution.failed", "execution.timeout"], "environments": ["prod"]}
    conditions      JSONB NOT NULL DEFAULT '{}',
    
    -- Channel configs: which channels and their settings
    -- e.g. [{"type": "email", "recipients": ["team@example.com"]}, {"type": "slack", "webhookUrl": "..."}]
    channels        JSONB NOT NULL DEFAULT '[]',
    
    -- Deduplication window (seconds) - suppress duplicate alerts within this window
    dedup_window_seconds INT NOT NULL DEFAULT 300,
    
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    
    UNIQUE(tenant_id, name)
);

-- Notification channel configurations (reusable across rules)
CREATE TABLE notification_channels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL,  -- 'email', 'slack', 'webhook', 'pagerduty'
    
    -- Channel-specific config
    -- Email: {"smtpHost": "...", "from": "...", "recipients": [...]}
    -- Slack: {"webhookUrl": "..."}
    -- Webhook: {"url": "...", "headers": {...}}
    config          JSONB NOT NULL,
    
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    
    UNIQUE(tenant_id, name)
);

-- Alert history (sent notifications for tracking and deduplication)
CREATE TABLE alert_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    alert_rule_id   UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    
    -- What triggered this alert
    event_type      VARCHAR(100) NOT NULL,
    event_payload   JSONB NOT NULL,
    
    -- Delivery status per channel
    -- [{"channel": "email", "status": "sent", "sentAt": "..."}, {"channel": "slack", "status": "failed", "error": "..."}]
    delivery_status JSONB NOT NULL DEFAULT '[]',
    
    -- Deduplication key (hash of rule + relevant event fields)
    dedup_key       VARCHAR(255) NOT NULL,
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Audit log (immutable, append-only)
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    
    -- Who performed the action
    actor_id        UUID,       -- NULL for system actions
    actor_type      VARCHAR(50) NOT NULL,  -- 'user', 'api_token', 'service', 'system'
    actor_name      VARCHAR(255),
    
    -- What was done
    action          VARCHAR(100) NOT NULL,  -- 'pipeline.created', 'execution.cancelled', 'user.login', etc.
    resource_type   VARCHAR(100) NOT NULL,  -- 'pipeline', 'execution', 'user', 'connection', etc.
    resource_id     UUID,
    resource_name   VARCHAR(255),
    
    -- Details of the change
    details         JSONB,  -- action-specific payload
    
    -- Request context
    ip_address      INET,
    user_agent      TEXT,
    request_id      VARCHAR(100),
    
    -- Immutable timestamp
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- In-app notifications (bell icon, notification center)
CREATE TABLE user_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    
    -- Notification content
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    type            VARCHAR(50) NOT NULL,  -- 'alert', 'info', 'success', 'warning'
    
    -- Link to related resource
    resource_type   VARCHAR(100),
    resource_id     UUID,
    link_url        VARCHAR(500),
    
    -- State
    read            BOOLEAN NOT NULL DEFAULT false,
    read_at         TIMESTAMPTZ,
    
    -- Source (optional link to alert)
    alert_history_id UUID REFERENCES alert_history(id) ON DELETE SET NULL,
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  -- auto-delete old notifications
);

-- User notification preferences
CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    
    -- Global preferences
    email_enabled   BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled  BOOLEAN NOT NULL DEFAULT true,
    
    -- Per-type preferences (JSON map of event_type -> enabled)
    -- e.g. {"execution.failed": true, "execution.completed": false}
    event_preferences JSONB NOT NULL DEFAULT '{}',
    
    -- Quiet hours (optional)
    quiet_hours_start TIME,
    quiet_hours_end   TIME,
    quiet_hours_tz    VARCHAR(50),
    
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(tenant_id, user_id)
);

-- Indexes for common queries
CREATE INDEX idx_alert_rules_tenant ON alert_rules(tenant_id);
CREATE INDEX idx_alert_rules_pipeline ON alert_rules(tenant_id, pipeline_id) WHERE pipeline_id IS NOT NULL;
CREATE INDEX idx_alert_history_tenant_created ON alert_history(tenant_id, created_at DESC);
CREATE INDEX idx_alert_history_dedup ON alert_history(tenant_id, alert_rule_id, dedup_key, created_at DESC);
CREATE INDEX idx_audit_log_tenant_created ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_tenant_action ON audit_log(tenant_id, action, created_at DESC);
CREATE INDEX idx_audit_log_tenant_resource ON audit_log(tenant_id, resource_type, resource_id);
CREATE INDEX idx_user_notifications_user ON user_notifications(tenant_id, user_id, created_at DESC);
CREATE INDEX idx_user_notifications_unread ON user_notifications(tenant_id, user_id, read, created_at DESC) WHERE read = false;

-- RLS policies (per ADR-013)
ALTER TABLE alert_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE notification_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_channels FORCE ROW LEVEL SECURITY;
ALTER TABLE alert_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_history FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
ALTER TABLE user_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_notifications FORCE ROW LEVEL SECURITY;
ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preferences FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_alert_rules ON alert_rules FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
CREATE POLICY tenant_isolation_channels ON notification_channels FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
CREATE POLICY tenant_isolation_alert_history ON alert_history FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
CREATE POLICY tenant_isolation_audit_log ON audit_log FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
CREATE POLICY tenant_isolation_user_notifications ON user_notifications FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
CREATE POLICY tenant_isolation_preferences ON notification_preferences FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

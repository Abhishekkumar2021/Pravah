-- US-10.01: Password reset tokens (single-use, time-limited)

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens(expires_at)
    WHERE used_at IS NULL;

COMMENT ON TABLE password_reset_tokens IS 'Single-use password reset tokens (store SHA-256 hash only)';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'SHA-256 hex of the raw token sent by email';

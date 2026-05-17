-- US-10.01: Email verification tokens (single-use, time-limited)

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_email_verification_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires ON email_verification_tokens(expires_at)
    WHERE used_at IS NULL;

COMMENT ON TABLE email_verification_tokens IS 'Single-use email verification tokens (store SHA-256 hash only)';
COMMENT ON COLUMN email_verification_tokens.token_hash IS 'SHA-256 hex of the raw token sent by email';

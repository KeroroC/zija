CREATE TABLE owner_recovery_token (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_owner_recovery_account
    ON owner_recovery_token(account_id, expires_at);

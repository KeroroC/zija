CREATE TABLE invitation (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    expires_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID NOT NULL REFERENCES account(id),
    consumed_at   TIMESTAMPTZ,
    consumed_by   UUID REFERENCES account(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invitation_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_invitation_consumption CHECK (
        (consumed_at IS NULL AND consumed_by IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_by IS NOT NULL)
    )
);

CREATE INDEX idx_invitation_household ON invitation(household_id);
CREATE INDEX idx_invitation_expires ON invitation(expires_at);

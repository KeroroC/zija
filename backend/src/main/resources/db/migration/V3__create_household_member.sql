CREATE TABLE household (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1,
    id            UUID NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    timezone      VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_household_singleton CHECK (singleton_key = 1)
);

CREATE TABLE member (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    role          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INT NOT NULL DEFAULT 0,
    UNIQUE(household_id, account_id),
    CONSTRAINT ck_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'))
);

CREATE INDEX idx_member_household ON member(household_id);
CREATE INDEX idx_member_account ON member(account_id);
CREATE INDEX idx_member_role ON member(household_id, role);
CREATE UNIQUE INDEX uq_member_single_owner
    ON member(household_id)
    WHERE role = 'OWNER';

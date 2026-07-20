CREATE TABLE account (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(50) NOT NULL,
    username_normalized VARCHAR(50) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(100) NOT NULL,
    email               VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_account_status ON account(status);

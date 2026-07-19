CREATE TABLE system_installation (
    singleton_key SMALLINT PRIMARY KEY,
    installation_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_installation_singleton CHECK (singleton_key = 1)
);

INSERT INTO system_installation (singleton_key, installation_id)
VALUES (1, gen_random_uuid());

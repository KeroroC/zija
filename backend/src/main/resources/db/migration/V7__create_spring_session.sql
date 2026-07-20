CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL UNIQUE,
    creation_time         BIGINT NOT NULL,
    last_access_time      BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time           BIGINT NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT pk_spring_session PRIMARY KEY (primary_id)
);

CREATE INDEX idx_spring_session_expiry ON spring_session(expiry_time);
CREATE INDEX idx_spring_session_principal_name
    ON spring_session(principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA NOT NULL,
    CONSTRAINT pk_spring_session_attributes PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT fk_spring_session_attributes_session
        FOREIGN KEY (session_primary_id)
        REFERENCES spring_session(primary_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_spring_session_attributes_session_primary_id
    ON spring_session_attributes(session_primary_id);

CREATE TABLE stored_file (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL REFERENCES household(id),
    storage_key         VARCHAR(160) NOT NULL UNIQUE,
    original_filename   VARCHAR(255) NOT NULL,
    declared_media_type VARCHAR(100),
    detected_media_type VARCHAR(100) NOT NULL,
    byte_size           BIGINT NOT NULL,
    sha256              CHAR(64) NOT NULL,
    reference_count     INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stored_file_media_type CHECK (
        detected_media_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT ck_stored_file_size CHECK (byte_size > 0 AND byte_size <= 5242880),
    CONSTRAINT ck_stored_file_references CHECK (reference_count >= 0)
);

CREATE INDEX idx_stored_file_household ON stored_file(household_id);

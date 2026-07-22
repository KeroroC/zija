CREATE TABLE location (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    parent_id       UUID,
    name            VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    ever_referenced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_location_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_location_name UNIQUE NULLS NOT DISTINCT
        (household_id, parent_id, name_normalized),
    CONSTRAINT fk_location_parent_same_household
        FOREIGN KEY (household_id, parent_id)
        REFERENCES location(household_id, id)
);

CREATE INDEX idx_location_household_parent_order
    ON location(household_id, parent_id, sort_order, id);

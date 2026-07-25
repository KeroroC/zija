CREATE TABLE inventory_stocktake (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by   UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    version      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_stocktake_status
        CHECK (status IN ('DRAFT','COMPLETED','CANCELLED')),
    CONSTRAINT uq_inventory_stocktake_household UNIQUE (household_id, id)
);

CREATE INDEX idx_inventory_stocktake_household_status
    ON inventory_stocktake(household_id, status, created_at);

CREATE TABLE inventory_stocktake_item (
    id               UUID PRIMARY KEY,
    stocktake_id     UUID NOT NULL,
    household_id     UUID NOT NULL,
    lot_id           UUID NOT NULL,
    location_id      UUID NOT NULL,
    book_quantity    NUMERIC(20,6) NOT NULL,
    actual_quantity  NUMERIC(20,6) NOT NULL,
    position_revision BIGINT NOT NULL,
    reason           VARCHAR(120),
    CONSTRAINT uq_inventory_stocktake_item
        UNIQUE (stocktake_id, lot_id, location_id),
    CONSTRAINT ck_inventory_stocktake_actual_nonneg CHECK (actual_quantity >= 0),
    CONSTRAINT fk_inventory_stocktake_item_stocktake FOREIGN KEY (stocktake_id)
        REFERENCES inventory_stocktake(id)
);

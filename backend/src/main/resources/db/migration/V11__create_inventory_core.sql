-- 批次
CREATE TABLE inventory_lot (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    item_id         UUID NOT NULL,
    purchase_date   DATE,
    production_date DATE,
    expiry_date     DATE,
    lot_number      VARCHAR(80),
    serial_number   VARCHAR(120),
    memo            VARCHAR(4000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_lot_household UNIQUE (household_id, id),
    CONSTRAINT fk_inventory_lot_item FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id)
);

CREATE INDEX idx_inventory_lot_household_item ON inventory_lot(household_id, item_id);
CREATE INDEX idx_inventory_lot_household_expiry ON inventory_lot(household_id, expiry_date);

-- 库存位（投影）
CREATE TABLE inventory_stock_position (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    lot_id       UUID NOT NULL,
    location_id  UUID NOT NULL,
    quantity     NUMERIC(20,6) NOT NULL,
    revision     BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_stock_position UNIQUE (household_id, lot_id, location_id),
    CONSTRAINT ck_inventory_stock_position_nonneg CHECK (quantity >= 0),
    CONSTRAINT fk_inventory_stock_position_lot FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_inventory_stock_position_location FOREIGN KEY (household_id, location_id)
        REFERENCES location(household_id, id)
);

CREATE INDEX idx_inventory_stock_position_household_location
    ON inventory_stock_position(household_id, location_id);
CREATE INDEX idx_inventory_stock_position_household_lot
    ON inventory_stock_position(household_id, lot_id);

-- 不可变库存流水
CREATE TABLE inventory_movement (
    id                 UUID PRIMARY KEY,
    household_id       UUID NOT NULL REFERENCES household(id),
    lot_id             UUID NOT NULL,
    item_id            UUID NOT NULL,
    type               VARCHAR(20) NOT NULL,
    quantity           NUMERIC(20,6) NOT NULL,
    from_location_id   UUID,
    to_location_id     UUID,
    reason             VARCHAR(120),
    memo               VARCHAR(4000),
    operator_account_id UUID NOT NULL,
    business_time      TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key    VARCHAR(100) NOT NULL,
    reversal_of        UUID,
    CONSTRAINT ck_inventory_movement_type
        CHECK (type IN ('INBOUND','CONSUME','LOSS','ADJUSTMENT','TRANSFER','REVERSAL')),
    CONSTRAINT ck_inventory_movement_quantity_pos CHECK (quantity > 0),
    CONSTRAINT fk_inventory_movement_lot FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_inventory_movement_reversal_of FOREIGN KEY (reversal_of)
        REFERENCES inventory_movement(id)
);

CREATE INDEX idx_inventory_movement_household_lot
    ON inventory_movement(household_id, lot_id, created_at);
CREATE INDEX idx_inventory_movement_household_item
    ON inventory_movement(household_id, item_id, created_at);
CREATE INDEX idx_inventory_movement_household_type
    ON inventory_movement(household_id, type, created_at);
CREATE INDEX idx_inventory_movement_household_location
    ON inventory_movement(household_id, coalesce(from_location_id, to_location_id));
CREATE INDEX idx_inventory_movement_idempotency
    ON inventory_movement(household_id, idempotency_key);
CREATE INDEX idx_inventory_movement_reversal_of
    ON inventory_movement(reversal_of);

-- 幂等结果登记
CREATE TABLE inventory_idempotency_record (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(120) NOT NULL,
    movement_id      UUID,
    response_payload JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_idempotency UNIQUE (household_id, idempotency_key)
);

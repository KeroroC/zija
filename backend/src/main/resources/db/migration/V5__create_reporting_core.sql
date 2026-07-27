-- V5__create_reporting_core.sql
-- reporting 模块核心表：事件去重、死信、搜索索引、库存流水扁平、库存位扁平

-- 1) 事件去重
CREATE TABLE reporting_processed_event (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_processed_event_type ON reporting_processed_event(event_type);
COMMENT ON TABLE reporting_processed_event IS 'reporting 模块事件去重登记';
COMMENT ON COLUMN reporting_processed_event.event_id IS '事件唯一 ID（来自源模块）';
COMMENT ON COLUMN reporting_processed_event.event_type IS '事件类型全限定名';
COMMENT ON COLUMN reporting_processed_event.processed_at IS '处理完成时间';

-- 2) 事件 dead-letter
CREATE TABLE reporting_event_dead_letter (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL,
    event_type      VARCHAR(80) NOT NULL,
    payload         JSONB NOT NULL,
    failure_count   INTEGER NOT NULL DEFAULT 1,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    last_error      VARCHAR(4000),
    last_retry_at   TIMESTAMPTZ,
    abandoned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reporting_dead_letter_event UNIQUE (event_id)
);
CREATE INDEX idx_reporting_dead_letter_retry ON reporting_event_dead_letter(abandoned, next_retry_at);
COMMENT ON TABLE reporting_event_dead_letter IS 'reporting 模块事件处理失败重试队列';
COMMENT ON COLUMN reporting_event_dead_letter.id IS '主键';
COMMENT ON COLUMN reporting_event_dead_letter.event_id IS '事件唯一 ID（来自源模块）';
COMMENT ON COLUMN reporting_event_dead_letter.event_type IS '事件类型全限定名';
COMMENT ON COLUMN reporting_event_dead_letter.payload IS '原始事件 JSON 序列化';
COMMENT ON COLUMN reporting_event_dead_letter.failure_count IS '累计失败次数';
COMMENT ON COLUMN reporting_event_dead_letter.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN reporting_event_dead_letter.last_error IS '最近一次错误信息';
COMMENT ON COLUMN reporting_event_dead_letter.last_retry_at IS '最近一次重试时间';
COMMENT ON COLUMN reporting_event_dead_letter.abandoned IS '超过最大重试次数后放弃';
COMMENT ON COLUMN reporting_event_dead_letter.created_at IS '创建时间';

-- 3) 全局搜索扁平读模型
CREATE TABLE reporting_search_index (
    household_id    UUID NOT NULL,
    entity_type     VARCHAR(20) NOT NULL,
    entity_id       UUID NOT NULL,
    item_name       VARCHAR(120),
    brand_name      VARCHAR(120),
    tag_names       VARCHAR(400),
    category_name   VARCHAR(120),
    unit_name       VARCHAR(40),
    lot_id          UUID,
    lot_number      VARCHAR(120),
    serial_number   VARCHAR(120),
    location_id     UUID,
    location_name   VARCHAR(120),
    location_path   VARCHAR(800),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, entity_type, entity_id)
);
CREATE INDEX idx_reporting_search_item_name ON reporting_search_index(household_id, entity_type, item_name);
CREATE INDEX idx_reporting_search_lot_number ON reporting_search_index(household_id, entity_type, lot_number);
CREATE INDEX idx_reporting_search_location ON reporting_search_index(household_id, entity_type, location_path);
COMMENT ON TABLE reporting_search_index IS '全局搜索扁平读模型';
COMMENT ON COLUMN reporting_search_index.household_id IS '所属家庭 ID';
COMMENT ON COLUMN reporting_search_index.entity_type IS 'ITEM | LOT | LOCATION';
COMMENT ON COLUMN reporting_search_index.entity_id IS '实体 ID';
COMMENT ON COLUMN reporting_search_index.item_name IS '物品名称';
COMMENT ON COLUMN reporting_search_index.brand_name IS '品牌名称';
COMMENT ON COLUMN reporting_search_index.tag_names IS '逗号分隔标签名';
COMMENT ON COLUMN reporting_search_index.category_name IS '分类名称';
COMMENT ON COLUMN reporting_search_index.unit_name IS '计量单位名称';
COMMENT ON COLUMN reporting_search_index.lot_id IS '批次 ID';
COMMENT ON COLUMN reporting_search_index.lot_number IS '批次编号';
COMMENT ON COLUMN reporting_search_index.serial_number IS '序列号';
COMMENT ON COLUMN reporting_search_index.location_id IS '库存位 ID';
COMMENT ON COLUMN reporting_search_index.location_name IS '库存位名称';
COMMENT ON COLUMN reporting_search_index.location_path IS '库存位路径';
COMMENT ON COLUMN reporting_search_index.updated_at IS '最后更新时间';

-- 4) 库存流水扁平读模型
CREATE TABLE reporting_movement_flat (
    household_id        UUID NOT NULL,
    movement_id         UUID NOT NULL PRIMARY KEY,
    event_id            UUID NOT NULL UNIQUE,
    lot_id              UUID NOT NULL,
    item_id             UUID NOT NULL,
    item_name           VARCHAR(120) NOT NULL,
    type                VARCHAR(20) NOT NULL,
    quantity_delta      NUMERIC(20,6) NOT NULL,
    from_location_id    UUID,
    to_location_id      UUID,
    from_location_path  VARCHAR(800),
    to_location_path    VARCHAR(800),
    operator_account_id UUID,
    operator_display_name VARCHAR(120),
    reason              VARCHAR(120),
    reversal_of         UUID,
    business_time       TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_movement_flat_time ON reporting_movement_flat(household_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_item ON reporting_movement_flat(household_id, item_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_operator ON reporting_movement_flat(household_id, operator_account_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_type ON reporting_movement_flat(household_id, type, business_time DESC);
COMMENT ON TABLE reporting_movement_flat IS '库存流水扁平读模型（reporting 投影）';
COMMENT ON COLUMN reporting_movement_flat.household_id IS '所属家庭 ID';
COMMENT ON COLUMN reporting_movement_flat.movement_id IS '库存变动 ID';
COMMENT ON COLUMN reporting_movement_flat.event_id IS '事件唯一 ID（来自源模块）';
COMMENT ON COLUMN reporting_movement_flat.lot_id IS '批次 ID';
COMMENT ON COLUMN reporting_movement_flat.item_id IS '物品 ID';
COMMENT ON COLUMN reporting_movement_flat.item_name IS '物品名称';
COMMENT ON COLUMN reporting_movement_flat.type IS 'INBOUND | CONSUME | DAMAGE | STOCKTAKE | MOVE | REVERSAL';
COMMENT ON COLUMN reporting_movement_flat.quantity_delta IS '数量变动（正为入库，负为出库）';
COMMENT ON COLUMN reporting_movement_flat.from_location_id IS '来源库存位 ID';
COMMENT ON COLUMN reporting_movement_flat.to_location_id IS '目标库存位 ID';
COMMENT ON COLUMN reporting_movement_flat.from_location_path IS '来源库存位路径';
COMMENT ON COLUMN reporting_movement_flat.to_location_path IS '目标库存位路径';
COMMENT ON COLUMN reporting_movement_flat.operator_account_id IS '操作人账户 ID';
COMMENT ON COLUMN reporting_movement_flat.operator_display_name IS '由 reporting 拉取 identity 信息填充';
COMMENT ON COLUMN reporting_movement_flat.reason IS '变动原因';
COMMENT ON COLUMN reporting_movement_flat.reversal_of IS '冲正的目标变动 ID';
COMMENT ON COLUMN reporting_movement_flat.business_time IS '业务时间';
COMMENT ON COLUMN reporting_movement_flat.created_at IS '创建时间';

-- 5) 库存位扁平读模型
CREATE TABLE reporting_stock_flat (
    household_id    UUID NOT NULL,
    lot_id          UUID NOT NULL,
    item_id         UUID NOT NULL,
    item_name       VARCHAR(120) NOT NULL,
    unit_name       VARCHAR(40) NOT NULL,
    lot_number      VARCHAR(120),
    serial_number   VARCHAR(120),
    expiry_date     DATE,
    location_id     UUID NOT NULL,
    location_path   VARCHAR(800) NOT NULL,
    quantity        NUMERIC(20,6) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, lot_id, location_id)
);
CREATE INDEX idx_reporting_stock_flat_expiry ON reporting_stock_flat(household_id, expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_reporting_stock_flat_item ON reporting_stock_flat(household_id, item_id);
COMMENT ON TABLE reporting_stock_flat IS '库存位扁平读模型（reporting 投影）';
COMMENT ON COLUMN reporting_stock_flat.household_id IS '所属家庭 ID';
COMMENT ON COLUMN reporting_stock_flat.lot_id IS '批次 ID';
COMMENT ON COLUMN reporting_stock_flat.item_id IS '物品 ID';
COMMENT ON COLUMN reporting_stock_flat.item_name IS '物品名称';
COMMENT ON COLUMN reporting_stock_flat.unit_name IS '计量单位名称';
COMMENT ON COLUMN reporting_stock_flat.lot_number IS '批次编号';
COMMENT ON COLUMN reporting_stock_flat.serial_number IS '序列号';
COMMENT ON COLUMN reporting_stock_flat.expiry_date IS '过期日期';
COMMENT ON COLUMN reporting_stock_flat.location_id IS '库存位 ID';
COMMENT ON COLUMN reporting_stock_flat.location_path IS '库存位路径';
COMMENT ON COLUMN reporting_stock_flat.quantity IS '库存数量';
COMMENT ON COLUMN reporting_stock_flat.updated_at IS '最后更新时间';

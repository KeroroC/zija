-- 分类（树形字典）
CREATE TABLE catalog_category (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    parent_id       UUID,
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_category_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_category_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_category_name UNIQUE NULLS NOT DISTINCT
        (household_id, parent_id, name_normalized),
    CONSTRAINT fk_catalog_category_parent_same_household
        FOREIGN KEY (household_id, parent_id)
        REFERENCES catalog_category(household_id, id)
);

-- 品牌
CREATE TABLE catalog_brand (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_brand_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_brand_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_brand_name UNIQUE (household_id, name_normalized)
);

-- 单位
CREATE TABLE catalog_unit (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    decimal_scale   SMALLINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_unit_scale CHECK (decimal_scale BETWEEN 0 AND 6),
    CONSTRAINT ck_catalog_unit_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_unit_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_unit_name UNIQUE (household_id, name_normalized)
);

-- 标签
CREATE TABLE catalog_tag (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_tag_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_tag_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_tag_name UNIQUE (household_id, name_normalized)
);

-- 物品
CREATE TABLE catalog_item (
    id                        UUID PRIMARY KEY,
    household_id              UUID NOT NULL REFERENCES household(id),
    name                      VARCHAR(120) NOT NULL,
    management_type           VARCHAR(20) NOT NULL,
    category_id               UUID,
    brand_id                  UUID,
    unit_id                   UUID NOT NULL,
    cover_file_id             UUID,
    memo                      VARCHAR(4000),
    expiry_reminder_mode      VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    expiry_reminder_days      SMALLINT[],
    low_stock_mode            VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    low_stock_threshold       NUMERIC(18, 6),
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_at               TIMESTAMPTZ,
    archived_by               UUID REFERENCES account(id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_item_type CHECK (
        management_type IN ('CONSUMABLE', 'DURABLE')
    ),
    CONSTRAINT ck_catalog_item_expiry_mode CHECK (
        expiry_reminder_mode IN ('INHERIT', 'DISABLED', 'CUSTOM')
    ),
    CONSTRAINT ck_catalog_item_low_stock_mode CHECK (
        low_stock_mode IN ('INHERIT', 'DISABLED', 'CUSTOM')
    ),
    CONSTRAINT ck_catalog_item_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_catalog_item_archive_state CHECK (
        (status = 'ACTIVE' AND archived_at IS NULL AND archived_by IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL AND archived_by IS NOT NULL)
    ),
    CONSTRAINT ck_catalog_item_expiry_config CHECK (
        (expiry_reminder_mode = 'CUSTOM' AND expiry_reminder_days IS NOT NULL)
        OR (expiry_reminder_mode <> 'CUSTOM' AND expiry_reminder_days IS NULL)
    ),
    CONSTRAINT ck_catalog_item_low_stock_config CHECK (
        (low_stock_mode = 'CUSTOM' AND low_stock_threshold IS NOT NULL)
        OR (low_stock_mode <> 'CUSTOM' AND low_stock_threshold IS NULL)
    ),
    CONSTRAINT uq_catalog_item_household_id UNIQUE (household_id, id),
    CONSTRAINT fk_catalog_item_category_same_household
        FOREIGN KEY (household_id, category_id)
        REFERENCES catalog_category(household_id, id),
    CONSTRAINT fk_catalog_item_brand_same_household
        FOREIGN KEY (household_id, brand_id)
        REFERENCES catalog_brand(household_id, id),
    CONSTRAINT fk_catalog_item_unit_same_household
        FOREIGN KEY (household_id, unit_id)
        REFERENCES catalog_unit(household_id, id)
);

-- 物品-标签关联
CREATE TABLE catalog_item_tag (
    household_id UUID NOT NULL REFERENCES household(id),
    item_id      UUID NOT NULL,
    tag_id       UUID NOT NULL,
    PRIMARY KEY (household_id, item_id, tag_id),
    CONSTRAINT fk_catalog_item_tag_item_same_household
        FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id),
    CONSTRAINT fk_catalog_item_tag_tag_same_household
        FOREIGN KEY (household_id, tag_id)
        REFERENCES catalog_tag(household_id, id)
);

-- 索引
CREATE INDEX idx_catalog_item_household_status
    ON catalog_item(household_id, status);
CREATE INDEX idx_catalog_item_category ON catalog_item(category_id);
CREATE INDEX idx_catalog_item_brand ON catalog_item(brand_id);
CREATE INDEX idx_catalog_item_unit ON catalog_item(unit_id);
CREATE INDEX idx_catalog_item_tag_tag ON catalog_item_tag(household_id, tag_id, item_id);

-- ============================================================
-- 知家 (zija) — 全量建表脚本
-- 合并自 V1-V12，包含所有业务表与 Spring Session 表
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 系统模块：安装信息
-- ────────────────────────────────────────────────────────────

CREATE TABLE system_installation (
    singleton_key SMALLINT PRIMARY KEY,
    installation_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_installation_singleton CHECK (singleton_key = 1)
);

COMMENT ON TABLE system_installation IS '系统安装信息（单例表，仅允许一行）';
COMMENT ON COLUMN system_installation.singleton_key IS '固定为 1，强制单例';
COMMENT ON COLUMN system_installation.installation_id IS '安装实例唯一 ID';
COMMENT ON COLUMN system_installation.created_at IS '创建时间';

INSERT INTO system_installation (singleton_key, installation_id)
VALUES (1, gen_random_uuid());

-- ────────────────────────────────────────────────────────────
-- 身份模块：账户
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE account IS '用户账户';
COMMENT ON COLUMN account.id IS '主键';
COMMENT ON COLUMN account.username IS '用户名';
COMMENT ON COLUMN account.username_normalized IS '规范化用户名（小写/trim）';
COMMENT ON COLUMN account.password_hash IS '密码哈希';
COMMENT ON COLUMN account.display_name IS '显示名称';
COMMENT ON COLUMN account.email IS '邮箱（可选）';
COMMENT ON COLUMN account.status IS '状态：ACTIVE / DISABLED';
COMMENT ON COLUMN account.created_at IS '创建时间';
COMMENT ON COLUMN account.updated_at IS '更新时间';
COMMENT ON COLUMN account.version IS '乐观锁版本号';

CREATE INDEX idx_account_status ON account(status);

-- ────────────────────────────────────────────────────────────
-- 家庭模块：家庭与成员
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE household IS '家庭（单例表，仅允许一个家庭）';
COMMENT ON COLUMN household.singleton_key IS '固定为 1，强制单例';
COMMENT ON COLUMN household.id IS '家庭唯一 ID';
COMMENT ON COLUMN household.name IS '家庭名称';
COMMENT ON COLUMN household.timezone IS '时区';
COMMENT ON COLUMN household.created_at IS '创建时间';
COMMENT ON COLUMN household.updated_at IS '更新时间';
COMMENT ON COLUMN household.version IS '乐观锁版本号';

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

COMMENT ON TABLE member IS '家庭成员（账户与家庭的多对多关联）';
COMMENT ON COLUMN member.id IS '主键';
COMMENT ON COLUMN member.household_id IS '所属家庭';
COMMENT ON COLUMN member.account_id IS '关联账户';
COMMENT ON COLUMN member.role IS '角色：OWNER / ADMIN / MEMBER';
COMMENT ON COLUMN member.status IS '状态：ACTIVE / DEACTIVATED';
COMMENT ON COLUMN member.created_at IS '创建时间';
COMMENT ON COLUMN member.updated_at IS '更新时间';
COMMENT ON COLUMN member.version IS '乐观锁版本号';

CREATE INDEX idx_member_household ON member(household_id);
CREATE INDEX idx_member_account ON member(account_id);
CREATE INDEX idx_member_role ON member(household_id, role);
CREATE UNIQUE INDEX uq_member_single_owner
    ON member(household_id)
    WHERE role = 'OWNER';

-- ────────────────────────────────────────────────────────────
-- 家庭模块：邀请
-- ────────────────────────────────────────────────────────────

CREATE TABLE invitation (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    expires_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID NOT NULL REFERENCES account(id),
    consumed_at   TIMESTAMPTZ,
    consumed_by   UUID REFERENCES account(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invitation_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_invitation_consumption CHECK (
        (consumed_at IS NULL AND consumed_by IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_by IS NOT NULL)
    )
);

COMMENT ON TABLE invitation IS '邀请码';
COMMENT ON COLUMN invitation.id IS '主键';
COMMENT ON COLUMN invitation.household_id IS '所属家庭';
COMMENT ON COLUMN invitation.token_digest IS '邀请令牌摘要';
COMMENT ON COLUMN invitation.role IS '目标角色：ADMIN / MEMBER';
COMMENT ON COLUMN invitation.expires_at IS '过期时间';
COMMENT ON COLUMN invitation.created_by IS '创建者';
COMMENT ON COLUMN invitation.consumed_at IS '消费时间';
COMMENT ON COLUMN invitation.consumed_by IS '消费者';
COMMENT ON COLUMN invitation.created_at IS '创建时间';

CREATE INDEX idx_invitation_household ON invitation(household_id);
CREATE INDEX idx_invitation_expires ON invitation(expires_at);

-- ────────────────────────────────────────────────────────────
-- 家庭模块：主人恢复令牌
-- ────────────────────────────────────────────────────────────

CREATE TABLE owner_recovery_token (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE owner_recovery_token IS '主人恢复令牌（用于找回 OWNER 身份）';
COMMENT ON COLUMN owner_recovery_token.id IS '主键';
COMMENT ON COLUMN owner_recovery_token.household_id IS '所属家庭';
COMMENT ON COLUMN owner_recovery_token.account_id IS '关联账户';
COMMENT ON COLUMN owner_recovery_token.token_digest IS '令牌摘要';
COMMENT ON COLUMN owner_recovery_token.expires_at IS '过期时间';
COMMENT ON COLUMN owner_recovery_token.consumed_at IS '消费时间';
COMMENT ON COLUMN owner_recovery_token.created_at IS '创建时间';

CREATE INDEX idx_owner_recovery_account
    ON owner_recovery_token(account_id, expires_at);

-- ────────────────────────────────────────────────────────────
-- 系统模块：审计日志
-- ────────────────────────────────────────────────────────────

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    household_id  UUID,
    actor_account_id UUID,
    subject_account_id UUID,
    action        VARCHAR(50) NOT NULL,
    outcome       VARCHAR(20) NOT NULL,
    detail        JSONB,
    ip_address    VARCHAR(45),
    request_id    VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

COMMENT ON TABLE audit_log IS '审计日志';
COMMENT ON COLUMN audit_log.id IS '主键';
COMMENT ON COLUMN audit_log.household_id IS '所属家庭（系统级操作可为空）';
COMMENT ON COLUMN audit_log.actor_account_id IS '操作者账户';
COMMENT ON COLUMN audit_log.subject_account_id IS '被操作者账户';
COMMENT ON COLUMN audit_log.action IS '操作类型';
COMMENT ON COLUMN audit_log.outcome IS '结果：SUCCESS / FAILURE';
COMMENT ON COLUMN audit_log.detail IS '详情（JSON）';
COMMENT ON COLUMN audit_log.ip_address IS 'IP 地址';
COMMENT ON COLUMN audit_log.request_id IS '请求追踪 ID';
COMMENT ON COLUMN audit_log.created_at IS '创建时间';

CREATE INDEX idx_audit_log_household ON audit_log(household_id);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_account_id);
CREATE INDEX idx_audit_log_subject ON audit_log(subject_account_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);

-- ────────────────────────────────────────────────────────────
-- 系统模块：Spring Session
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE spring_session IS 'Spring Session 主表（JDBC Session 存储）';
COMMENT ON COLUMN spring_session.primary_id IS '主键';
COMMENT ON COLUMN spring_session.session_id IS '会话 ID';
COMMENT ON COLUMN spring_session.creation_time IS '创建时间戳';
COMMENT ON COLUMN spring_session.last_access_time IS '最后访问时间戳';
COMMENT ON COLUMN spring_session.max_inactive_interval IS '最大不活跃间隔（秒）';
COMMENT ON COLUMN spring_session.expiry_time IS '过期时间戳';
COMMENT ON COLUMN spring_session.principal_name IS '认证主体名称';

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

COMMENT ON TABLE spring_session_attributes IS 'Spring Session 属性表';
COMMENT ON COLUMN spring_session_attributes.session_primary_id IS '所属会话主键';
COMMENT ON COLUMN spring_session_attributes.attribute_name IS '属性名';
COMMENT ON COLUMN spring_session_attributes.attribute_bytes IS '属性值（序列化）';

CREATE INDEX idx_spring_session_attributes_session_primary_id
    ON spring_session_attributes(session_primary_id);

-- ────────────────────────────────────────────────────────────
-- 文件模块：文件存储
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE stored_file IS '已存储文件元数据';
COMMENT ON COLUMN stored_file.id IS '主键';
COMMENT ON COLUMN stored_file.household_id IS '所属家庭';
COMMENT ON COLUMN stored_file.storage_key IS '存储路径键';
COMMENT ON COLUMN stored_file.original_filename IS '原始文件名';
COMMENT ON COLUMN stored_file.declared_media_type IS '声明的 MIME 类型';
COMMENT ON COLUMN stored_file.detected_media_type IS '检测到的 MIME 类型';
COMMENT ON COLUMN stored_file.byte_size IS '文件大小（字节）';
COMMENT ON COLUMN stored_file.sha256 IS 'SHA-256 哈希';
COMMENT ON COLUMN stored_file.reference_count IS '引用计数';
COMMENT ON COLUMN stored_file.created_at IS '创建时间';

CREATE INDEX idx_stored_file_household ON stored_file(household_id);

-- ────────────────────────────────────────────────────────────
-- 目录模块：分类、品牌、单位、标签、物品
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE catalog_category IS '分类（树形字典）';
COMMENT ON COLUMN catalog_category.id IS '主键';
COMMENT ON COLUMN catalog_category.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_category.parent_id IS '父分类 ID（NULL 为根节点）';
COMMENT ON COLUMN catalog_category.name IS '分类名称';
COMMENT ON COLUMN catalog_category.name_normalized IS '规范化名称';
COMMENT ON COLUMN catalog_category.status IS '状态：ACTIVE / ARCHIVED';
COMMENT ON COLUMN catalog_category.sort_order IS '排序序号';
COMMENT ON COLUMN catalog_category.created_at IS '创建时间';
COMMENT ON COLUMN catalog_category.updated_at IS '更新时间';
COMMENT ON COLUMN catalog_category.version IS '乐观锁版本号';

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

COMMENT ON TABLE catalog_brand IS '品牌';
COMMENT ON COLUMN catalog_brand.id IS '主键';
COMMENT ON COLUMN catalog_brand.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_brand.name IS '品牌名称';
COMMENT ON COLUMN catalog_brand.name_normalized IS '规范化名称';
COMMENT ON COLUMN catalog_brand.status IS '状态：ACTIVE / ARCHIVED';
COMMENT ON COLUMN catalog_brand.created_at IS '创建时间';
COMMENT ON COLUMN catalog_brand.updated_at IS '更新时间';
COMMENT ON COLUMN catalog_brand.version IS '乐观锁版本号';

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

COMMENT ON TABLE catalog_unit IS '单位';
COMMENT ON COLUMN catalog_unit.id IS '主键';
COMMENT ON COLUMN catalog_unit.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_unit.name IS '单位名称';
COMMENT ON COLUMN catalog_unit.name_normalized IS '规范化名称';
COMMENT ON COLUMN catalog_unit.decimal_scale IS '小数精度（0-6）';
COMMENT ON COLUMN catalog_unit.status IS '状态：ACTIVE / ARCHIVED';
COMMENT ON COLUMN catalog_unit.created_at IS '创建时间';
COMMENT ON COLUMN catalog_unit.updated_at IS '更新时间';
COMMENT ON COLUMN catalog_unit.version IS '乐观锁版本号';

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

COMMENT ON TABLE catalog_tag IS '标签';
COMMENT ON COLUMN catalog_tag.id IS '主键';
COMMENT ON COLUMN catalog_tag.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_tag.name IS '标签名称';
COMMENT ON COLUMN catalog_tag.name_normalized IS '规范化名称';
COMMENT ON COLUMN catalog_tag.status IS '状态：ACTIVE / ARCHIVED';
COMMENT ON COLUMN catalog_tag.created_at IS '创建时间';
COMMENT ON COLUMN catalog_tag.updated_at IS '更新时间';
COMMENT ON COLUMN catalog_tag.version IS '乐观锁版本号';

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

COMMENT ON TABLE catalog_item IS '物品';
COMMENT ON COLUMN catalog_item.id IS '主键';
COMMENT ON COLUMN catalog_item.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_item.name IS '物品名称';
COMMENT ON COLUMN catalog_item.management_type IS '管理类型：CONSUMABLE / DURABLE';
COMMENT ON COLUMN catalog_item.category_id IS '分类 ID';
COMMENT ON COLUMN catalog_item.brand_id IS '品牌 ID';
COMMENT ON COLUMN catalog_item.unit_id IS '单位 ID';
COMMENT ON COLUMN catalog_item.cover_file_id IS '封面文件 ID';
COMMENT ON COLUMN catalog_item.memo IS '备注';
COMMENT ON COLUMN catalog_item.expiry_reminder_mode IS '过期提醒模式：INHERIT / DISABLED / CUSTOM';
COMMENT ON COLUMN catalog_item.expiry_reminder_days IS '过期提醒天数（CUSTOM 模式）';
COMMENT ON COLUMN catalog_item.low_stock_mode IS '低库存提醒模式：INHERIT / DISABLED / CUSTOM';
COMMENT ON COLUMN catalog_item.low_stock_threshold IS '低库存阈值（CUSTOM 模式）';
COMMENT ON COLUMN catalog_item.status IS '状态：ACTIVE / ARCHIVED';
COMMENT ON COLUMN catalog_item.archived_at IS '归档时间';
COMMENT ON COLUMN catalog_item.archived_by IS '归档操作者';
COMMENT ON COLUMN catalog_item.created_at IS '创建时间';
COMMENT ON COLUMN catalog_item.updated_at IS '更新时间';
COMMENT ON COLUMN catalog_item.version IS '乐观锁版本号';

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

COMMENT ON TABLE catalog_item_tag IS '物品-标签关联';
COMMENT ON COLUMN catalog_item_tag.household_id IS '所属家庭';
COMMENT ON COLUMN catalog_item_tag.item_id IS '物品 ID';
COMMENT ON COLUMN catalog_item_tag.tag_id IS '标签 ID';

CREATE INDEX idx_catalog_item_household_status
    ON catalog_item(household_id, status);
CREATE INDEX idx_catalog_item_category ON catalog_item(category_id);
CREATE INDEX idx_catalog_item_brand ON catalog_item(brand_id);
CREATE INDEX idx_catalog_item_unit ON catalog_item(unit_id);
CREATE INDEX idx_catalog_item_tag_tag ON catalog_item_tag(household_id, tag_id, item_id);

-- ────────────────────────────────────────────────────────────
-- 位置模块：存储位置
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE location IS '存储位置（树形结构）';
COMMENT ON COLUMN location.id IS '主键';
COMMENT ON COLUMN location.household_id IS '所属家庭';
COMMENT ON COLUMN location.parent_id IS '父位置 ID（NULL 为根节点）';
COMMENT ON COLUMN location.name IS '位置名称';
COMMENT ON COLUMN location.name_normalized IS '规范化名称';
COMMENT ON COLUMN location.sort_order IS '排序序号';
COMMENT ON COLUMN location.ever_referenced IS '是否曾被库存引用';
COMMENT ON COLUMN location.created_at IS '创建时间';
COMMENT ON COLUMN location.updated_at IS '更新时间';
COMMENT ON COLUMN location.version IS '乐观锁版本号';

CREATE INDEX idx_location_household_parent_order
    ON location(household_id, parent_id, sort_order, id);

-- ────────────────────────────────────────────────────────────
-- 库存模块：批次、库存位、流水、幂等
-- ────────────────────────────────────────────────────────────

CREATE TABLE inventory_lot (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    item_id         UUID NOT NULL,
    purchase_date   DATE,
    production_date DATE,
    expiry_date     DATE,
    lot_number      VARCHAR(80) NOT NULL,
    serial_number   VARCHAR(120),
    memo            VARCHAR(4000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_lot_household UNIQUE (household_id, id),
    CONSTRAINT uq_inventory_lot_number UNIQUE (lot_number),
    CONSTRAINT fk_inventory_lot_item FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id)
);

COMMENT ON TABLE inventory_lot IS '批次（物品的采购/生产批次）';
COMMENT ON COLUMN inventory_lot.id IS '主键';
COMMENT ON COLUMN inventory_lot.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_lot.item_id IS '所属物品';
COMMENT ON COLUMN inventory_lot.purchase_date IS '采购日期';
COMMENT ON COLUMN inventory_lot.production_date IS '生产日期';
COMMENT ON COLUMN inventory_lot.expiry_date IS '过期日期';
COMMENT ON COLUMN inventory_lot.lot_number IS '批次号（自动生成，全局唯一）';
COMMENT ON COLUMN inventory_lot.serial_number IS '序列号';
COMMENT ON COLUMN inventory_lot.memo IS '备注';
COMMENT ON COLUMN inventory_lot.created_at IS '创建时间';
COMMENT ON COLUMN inventory_lot.updated_at IS '更新时间';
COMMENT ON COLUMN inventory_lot.version IS '乐观锁版本号';

CREATE INDEX idx_inventory_lot_household_item ON inventory_lot(household_id, item_id);
CREATE INDEX idx_inventory_lot_household_expiry ON inventory_lot(household_id, expiry_date);

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

COMMENT ON TABLE inventory_stock_position IS '库存位（批次在某位置的当前库存量投影）';
COMMENT ON COLUMN inventory_stock_position.id IS '主键';
COMMENT ON COLUMN inventory_stock_position.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_stock_position.lot_id IS '批次 ID';
COMMENT ON COLUMN inventory_stock_position.location_id IS '位置 ID';
COMMENT ON COLUMN inventory_stock_position.quantity IS '当前数量';
COMMENT ON COLUMN inventory_stock_position.revision IS '修订号（用于 FOR UPDATE 乐观控制）';
COMMENT ON COLUMN inventory_stock_position.created_at IS '创建时间';
COMMENT ON COLUMN inventory_stock_position.updated_at IS '更新时间';

CREATE INDEX idx_inventory_stock_position_household_location
    ON inventory_stock_position(household_id, location_id);
CREATE INDEX idx_inventory_stock_position_household_lot
    ON inventory_stock_position(household_id, lot_id);

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

COMMENT ON TABLE inventory_movement IS '不可变库存流水（所有库存变动的源头）';
COMMENT ON COLUMN inventory_movement.id IS '主键';
COMMENT ON COLUMN inventory_movement.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_movement.lot_id IS '批次 ID';
COMMENT ON COLUMN inventory_movement.item_id IS '物品 ID';
COMMENT ON COLUMN inventory_movement.type IS '类型：INBOUND / CONSUME / LOSS / ADJUSTMENT / TRANSFER / REVERSAL';
COMMENT ON COLUMN inventory_movement.quantity IS '数量（正数）';
COMMENT ON COLUMN inventory_movement.from_location_id IS '来源位置（INBOUND 为空）';
COMMENT ON COLUMN inventory_movement.to_location_id IS '目标位置（CONSUME/LOSS 为空）';
COMMENT ON COLUMN inventory_movement.reason IS '原因';
COMMENT ON COLUMN inventory_movement.memo IS '备注';
COMMENT ON COLUMN inventory_movement.operator_account_id IS '操作者账户';
COMMENT ON COLUMN inventory_movement.business_time IS '业务时间';
COMMENT ON COLUMN inventory_movement.created_at IS '创建时间';
COMMENT ON COLUMN inventory_movement.idempotency_key IS '幂等键';
COMMENT ON COLUMN inventory_movement.reversal_of IS '冲正目标流水 ID';

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

COMMENT ON TABLE inventory_idempotency_record IS '幂等结果登记（防止重复提交）';
COMMENT ON COLUMN inventory_idempotency_record.id IS '主键';
COMMENT ON COLUMN inventory_idempotency_record.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_idempotency_record.idempotency_key IS '幂等键';
COMMENT ON COLUMN inventory_idempotency_record.request_hash IS '请求体哈希';
COMMENT ON COLUMN inventory_idempotency_record.movement_id IS '关联流水 ID';
COMMENT ON COLUMN inventory_idempotency_record.response_payload IS '响应缓存';
COMMENT ON COLUMN inventory_idempotency_record.created_at IS '创建时间';

-- ────────────────────────────────────────────────────────────
-- 库存模块：盘点
-- ────────────────────────────────────────────────────────────

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

COMMENT ON TABLE inventory_stocktake IS '盘点单';
COMMENT ON COLUMN inventory_stocktake.id IS '主键';
COMMENT ON COLUMN inventory_stocktake.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_stocktake.status IS '状态：DRAFT / COMPLETED / CANCELLED';
COMMENT ON COLUMN inventory_stocktake.created_by IS '创建者';
COMMENT ON COLUMN inventory_stocktake.created_at IS '创建时间';
COMMENT ON COLUMN inventory_stocktake.updated_at IS '更新时间';
COMMENT ON COLUMN inventory_stocktake.completed_at IS '完成时间';
COMMENT ON COLUMN inventory_stocktake.version IS '乐观锁版本号';

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

COMMENT ON TABLE inventory_stocktake_item IS '盘点明细（每个批次+位置一条）';
COMMENT ON COLUMN inventory_stocktake_item.id IS '主键';
COMMENT ON COLUMN inventory_stocktake_item.stocktake_id IS '盘点单 ID';
COMMENT ON COLUMN inventory_stocktake_item.household_id IS '所属家庭';
COMMENT ON COLUMN inventory_stocktake_item.lot_id IS '批次 ID';
COMMENT ON COLUMN inventory_stocktake_item.location_id IS '位置 ID';
COMMENT ON COLUMN inventory_stocktake_item.book_quantity IS '账面数量';
COMMENT ON COLUMN inventory_stocktake_item.actual_quantity IS '实盘数量';
COMMENT ON COLUMN inventory_stocktake_item.position_revision IS '快照时库存位修订号';
COMMENT ON COLUMN inventory_stocktake_item.reason IS '差异原因';

-- 1) 家庭默认提醒规则（家庭单例）
CREATE TABLE reminder_household_rule (
    id                    UUID PRIMARY KEY,
    household_id          UUID NOT NULL UNIQUE REFERENCES household(id),
    expiry_disabled       BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_reminder_days SMALLINT[] NOT NULL DEFAULT ARRAY[30,7,1]::SMALLINT[],
    low_stock_disabled    BOOLEAN NOT NULL DEFAULT FALSE,
    low_stock_threshold   NUMERIC(20,6) NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INTEGER NOT NULL DEFAULT 0
);
ALTER TABLE reminder_household_rule ADD CONSTRAINT ck_reminder_rule_expiry_days
    CHECK (expiry_disabled OR (
        array_length(expiry_reminder_days,1) IS NOT NULL
        AND expiry_reminder_days <@ ARRAY(SELECT generate_series(1,3650)::SMALLINT)
        AND array_length(expiry_reminder_days,1) =
            (SELECT count(DISTINCT d) FROM unnest(expiry_reminder_days) d)
    ));
ALTER TABLE reminder_household_rule ADD CONSTRAINT ck_reminder_rule_low_stock
    CHECK (low_stock_disabled OR low_stock_threshold > 0);

-- 2) 提醒任务（单表 + kind，未完状态唯一软合并，保留历史 DONE/IGNORED）
CREATE TABLE reminder_task (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL REFERENCES household(id),
    kind                VARCHAR(20) NOT NULL,
    lot_id              UUID,
    item_id             UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    due_at              TIMESTAMPTZ NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    threshold_snapshot  JSONB,
    qty_snapshot        NUMERIC(20,6),
    snoozed_until       TIMESTAMPTZ,
    last_reconciled_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_reminder_task_kind     CHECK (kind IN ('EXPIRY','LOW_STOCK')),
    CONSTRAINT ck_reminder_task_status   CHECK (status IN ('OPEN','SNOOZED','DONE','IGNORED')),
    CONSTRAINT ck_reminder_task_severity CHECK (severity IN ('INFO','WARN','URGENT')),
    CONSTRAINT ck_reminder_task_lot_xor  CHECK (
        (kind = 'EXPIRY'    AND lot_id IS NOT NULL)
     OR (kind = 'LOW_STOCK' AND lot_id IS NULL)
    ),
    CONSTRAINT fk_reminder_task_lot  FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_reminder_task_item FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id)
);
CREATE UNIQUE INDEX uq_reminder_task_open
    ON reminder_task(household_id, kind, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'))
    WHERE status IN ('OPEN','SNOOZED');
CREATE INDEX idx_reminder_task_household_status_due
    ON reminder_task(household_id, status, due_at);
CREATE INDEX idx_reminder_task_item ON reminder_task(household_id, item_id);

-- 3) 站内通知
CREATE TABLE reminder_notification (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    scope           VARCHAR(20) NOT NULL,
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(4000),
    source_task_id  UUID,
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMPTZ,
    CONSTRAINT fk_reminder_notif_task FOREIGN KEY (source_task_id) REFERENCES reminder_task(id)
);
CREATE INDEX idx_reminder_notif_household_unread
    ON reminder_notification(household_id, read, created_at DESC);

-- 4) 事件去重 + dead-letter 重投
CREATE TABLE reminder_processed_event (
    event_id        UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reminder_event_dead_letter (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL,
    payload         JSONB NOT NULL,
    failure_count   INTEGER NOT NULL DEFAULT 1,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    last_error      VARCHAR(4000),
    last_retry_at   TIMESTAMPTZ,
    abandoned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reminder_dead_letter_event UNIQUE (event_id)
);

-- 知识来源：成员从已有附件中显式选定的、可参与家庭问答的派生用途。
-- 与 ai_knowledge_chunk（可重建派生数据）不同，本表保存用户意图与用户可见的准备状态：
-- 状态 PROCESSING / AVAILABLE / FAILED / DISABLED，失败原因、有限自动重试与手动重试的调度字段。
-- 附件生命周期（回收/恢复/改挂/永久删除）通过 file 模块公开事件同步本表与分块。
CREATE TABLE ai_knowledge_source (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL,
    file_id             UUID NOT NULL,
    mount_type          VARCHAR(20) NOT NULL,
    mount_id            UUID NOT NULL,
    status              VARCHAR(20) NOT NULL,
    failure_code        VARCHAR(60),
    failure_message     TEXT,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ,
    disabled_reason     VARCHAR(20),
    selected_at         TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ,
    processing_version  INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_knowledge_source_file UNIQUE (household_id, file_id),
    CONSTRAINT ck_ai_knowledge_source_status CHECK (status IN ('PROCESSING', 'AVAILABLE', 'FAILED', 'DISABLED')),
    CONSTRAINT ck_ai_knowledge_source_mount CHECK (mount_type IN ('HOUSEHOLD', 'ITEM', 'LOT')),
    CONSTRAINT ck_ai_knowledge_source_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_ai_knowledge_source_processing CHECK (processing_version >= 0)
);

COMMENT ON TABLE ai_knowledge_source IS 'AI 知识来源选择与准备状态（跟随附件生命周期）';
COMMENT ON COLUMN ai_knowledge_source.status IS 'PROCESSING 处理中 / AVAILABLE 可用 / FAILED 失败 / DISABLED 已停用';
COMMENT ON COLUMN ai_knowledge_source.failure_code IS '稳定失败原因码（TEXT_NOT_EXTRACTABLE / PROVIDER_UNAVAILABLE 等）';
COMMENT ON COLUMN ai_knowledge_source.disabled_reason IS '停用原因：CANCELLED 成员取消 / RECYCLED 附件进入回收站';
COMMENT ON COLUMN ai_knowledge_source.next_attempt_at IS '下次自动处理/重试时间；处理认领期间为处理租约';
COMMENT ON COLUMN ai_knowledge_source.processing_version IS '成功处理的版本号，随分块元数据写入，用于依据溯源';

CREATE INDEX idx_ai_knowledge_source_household ON ai_knowledge_source (household_id);
CREATE INDEX idx_ai_knowledge_source_due ON ai_knowledge_source (status, next_attempt_at);

-- Embedding baseline changes invalidate all derived vectors. Keep explicit selections,
-- but requeue every source that is not disabled for the new model and dimension.
UPDATE ai_knowledge_source
SET status = 'PROCESSING',
    failure_code = NULL,
    failure_message = NULL,
    attempt_count = 0,
    next_attempt_at = CURRENT_TIMESTAMP,
    disabled_reason = NULL,
    processed_at = NULL,
    processing_version = processing_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE status <> 'DISABLED';

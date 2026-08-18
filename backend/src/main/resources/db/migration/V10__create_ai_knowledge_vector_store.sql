-- AI knowledge chunks use Spring AI's standard PgVectorStore contract:
-- id, content, metadata and embedding. Domain metadata remains in JSONB so the
-- vector adapter can write documents without leaking AI types into business modules.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ai_knowledge_chunk (
    id                    UUID PRIMARY KEY,
    content               TEXT NOT NULL,
    metadata              JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding             vector(1024) NOT NULL,
    household_id          UUID GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'household_id', '')::uuid
    ) STORED,
    mount_type            VARCHAR(20) GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'mount_type', '')
    ) STORED,
    mount_id              UUID GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'mount_id', '')::uuid
    ) STORED,
    item_id               UUID GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'item_id', '')::uuid
    ) STORED,
    lot_id                UUID GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'lot_id', '')::uuid
    ) STORED,
    attachment_id         VARCHAR(100) GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'attachment_id', '')
    ) STORED,
    readiness_status      VARCHAR(20) GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'readiness_status', '')
    ) STORED,
    page_number           INTEGER GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'page_number', '')::integer
    ) STORED,
    section_path          VARCHAR(500) GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'section_path', '')
    ) STORED,
    char_start            INTEGER GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'char_start', '')::integer
    ) STORED,
    char_end              INTEGER GENERATED ALWAYS AS (
        NULLIF(metadata ->> 'char_end', '')::integer
    ) STORED,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_knowledge_chunk IS 'AI 知识来源分块与向量（可重建派生数据）';
COMMENT ON COLUMN ai_knowledge_chunk.metadata IS
    '服务端生成的过滤与引用元数据：household_id、mount_type、mount_id、item_id、lot_id、attachment_id、readiness_status、page_number、section_path、char_start、char_end、embedding_model、embedding_dimensions、chunker_version';
COMMENT ON COLUMN ai_knowledge_chunk.embedding IS '当前 embedding 模型生成的 1024 维向量';

CREATE INDEX idx_ai_knowledge_chunk_scope
    ON ai_knowledge_chunk(household_id, readiness_status, mount_type, mount_id);
CREATE INDEX idx_ai_knowledge_chunk_item_lot
    ON ai_knowledge_chunk(household_id, item_id, lot_id);
CREATE INDEX idx_ai_knowledge_chunk_attachment
    ON ai_knowledge_chunk(household_id, attachment_id);
CREATE INDEX idx_ai_knowledge_chunk_metadata
    ON ai_knowledge_chunk USING GIN(metadata);
CREATE INDEX idx_ai_knowledge_chunk_embedding_hnsw
    ON ai_knowledge_chunk USING HNSW (embedding vector_cosine_ops);

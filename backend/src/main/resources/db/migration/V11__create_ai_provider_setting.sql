-- Deployment-level AI provider configuration. Secrets are write-only through the HTTP API.
CREATE TABLE ai_provider_setting (
    singleton_key             SMALLINT PRIMARY KEY,
    enabled                   BOOLEAN NOT NULL DEFAULT FALSE,
    provider_id               VARCHAR(50) NOT NULL DEFAULT 'ollama',
    provider_credential       TEXT,
    outbound_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    requests_per_minute       INTEGER NOT NULL DEFAULT 20,
    max_context_tokens        INTEGER NOT NULL DEFAULT 8192,
    max_concurrent_requests   INTEGER NOT NULL DEFAULT 2,
    request_timeout_seconds   INTEGER NOT NULL DEFAULT 30,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_provider_setting_singleton CHECK (singleton_key = 1),
    CONSTRAINT ck_ai_provider_setting_provider CHECK (length(trim(provider_id)) > 0),
    CONSTRAINT ck_ai_provider_setting_requests CHECK (requests_per_minute BETWEEN 1 AND 600),
    CONSTRAINT ck_ai_provider_setting_context CHECK (max_context_tokens BETWEEN 256 AND 131072),
    CONSTRAINT ck_ai_provider_setting_concurrency CHECK (max_concurrent_requests BETWEEN 1 AND 32),
    CONSTRAINT ck_ai_provider_setting_timeout CHECK (request_timeout_seconds BETWEEN 1 AND 300)
);

COMMENT ON TABLE ai_provider_setting IS '部署级 AI 模型提供方设置（单例）';
COMMENT ON COLUMN ai_provider_setting.provider_credential IS '提供方凭据；HTTP 响应与审计日志不得回显';

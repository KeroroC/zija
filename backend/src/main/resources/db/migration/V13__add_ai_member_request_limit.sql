ALTER TABLE ai_provider_setting
    ADD COLUMN member_requests_per_minute INTEGER NOT NULL DEFAULT 10;

ALTER TABLE ai_provider_setting
    ADD CONSTRAINT ck_ai_provider_setting_member_requests
        CHECK (member_requests_per_minute BETWEEN 1 AND 600);

COMMENT ON COLUMN ai_provider_setting.member_requests_per_minute
    IS '单个成员每分钟允许发起的 AI 问答请求数';

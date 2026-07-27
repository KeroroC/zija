-- 家庭邮件提醒设置（家庭单例）
CREATE TABLE reminder_household_mail_setting (
    id                    UUID PRIMARY KEY,
    household_id          UUID NOT NULL UNIQUE REFERENCES household(id),
    digest_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    digest_frequency      VARCHAR(20) NOT NULL DEFAULT 'DAILY' CHECK (digest_frequency IN ('DAILY', 'WEEKLY')),
    urgent_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    recipient_roles       VARCHAR(20)[] NOT NULL DEFAULT ARRAY['OWNER']::VARCHAR(20)[],
    last_digest_sent_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INTEGER NOT NULL DEFAULT 0
);


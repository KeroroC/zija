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

CREATE INDEX idx_audit_log_household ON audit_log(household_id);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_account_id);
CREATE INDEX idx_audit_log_subject ON audit_log(subject_account_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);

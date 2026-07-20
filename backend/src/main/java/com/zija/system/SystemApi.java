package com.zija.system;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemApi {

    SystemSnapshot current();

    void recordAudit(SystemApi.AuditEvent event);

    record SystemSnapshot(
            String application,
            String version,
            String status,
            UUID installationId,
            OffsetDateTime databaseTime
    ) {
    }

    record AuditEvent(
            String action,
            String outcome,
            UUID householdId,
            UUID actorAccountId,
            UUID subjectAccountId,
            String requestId,
            String ipAddress,
            java.util.Map<String, Object> detail
    ) {
    }
}

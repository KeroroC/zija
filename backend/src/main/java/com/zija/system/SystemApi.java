package com.zija.system;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SystemApi {

    SystemSnapshot current();

    void recordAudit(SystemApi.AuditEvent event);

    AuditLogPage queryAuditLogs(
            UUID householdId,
            OffsetDateTime from,
            OffsetDateTime to,
            String action,
            UUID actorAccountId,
            String outcome,
            int page,
            int pageSize
    );

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
            Map<String, Object> detail
    ) {
    }

    record AuditLogItem(
            UUID id,
            String action,
            String outcome,
            UUID actorAccountId,
            UUID subjectAccountId,
            Map<String, Object> detail,
            String ipAddress,
            String requestId,
            OffsetDateTime createdAt
    ) {
    }

    record AuditLogPage(
            List<AuditLogItem> items,
            long total,
            int page,
            int pageSize
    ) {
    }
}

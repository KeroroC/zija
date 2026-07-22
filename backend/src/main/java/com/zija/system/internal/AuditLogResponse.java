package com.zija.system.internal;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

record AuditLogResponse(
        UUID id,
        String action,
        String outcome,
        UUID actorAccountId,
        String actorDisplayName,
        UUID subjectAccountId,
        String subjectDisplayName,
        Map<String, Object> detail,
        String ipAddress,
        String requestId,
        OffsetDateTime createdAt
) {
}

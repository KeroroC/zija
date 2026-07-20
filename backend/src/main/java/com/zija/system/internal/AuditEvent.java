package com.zija.system.internal;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        String action,
        String outcome,
        UUID householdId,
        UUID actorAccountId,
        UUID subjectAccountId,
        String requestId,
        String ipAddress,
        Map<String, Object> detail
) {
    public AuditEvent {
        if (outcome == null
                || (!outcome.equals("SUCCESS") && !outcome.equals("FAILURE"))) {
            throw new IllegalArgumentException("outcome must be SUCCESS or FAILURE");
        }
    }
}

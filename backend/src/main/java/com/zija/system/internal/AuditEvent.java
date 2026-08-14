package com.zija.system.internal;

import com.zija.shared.ZijaAuditOutcome;
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
                || (!outcome.equals(ZijaAuditOutcome.SUCCESS) && !outcome.equals(ZijaAuditOutcome.FAILURE))) {
            throw new IllegalArgumentException("outcome must be SUCCESS or FAILURE");
        }
    }
}

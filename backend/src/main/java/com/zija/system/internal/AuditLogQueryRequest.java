package com.zija.system.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

record AuditLogQueryRequest(
        UUID householdId,
        OffsetDateTime from,
        OffsetDateTime to,
        String action,
        UUID actorAccountId,
        String outcome
) {
}

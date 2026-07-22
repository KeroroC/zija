package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {

    private final AuditLogMapper auditLogMapper;

    AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional
    public void recordAudit(SystemApi.AuditEvent event) {
        auditLogMapper.insert(new AuditEvent(
                event.action(),
                event.outcome(),
                event.householdId(),
                event.actorAccountId(),
                event.subjectAccountId(),
                event.requestId(),
                event.ipAddress(),
                event.detail()
        ));
    }
}

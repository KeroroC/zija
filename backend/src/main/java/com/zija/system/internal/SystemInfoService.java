package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.SystemInstallationMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SystemInfoService implements SystemApi {

    private final SystemInstallationMapper installationMapper;
    private final Environment environment;
    private final AuditService auditService;

    SystemInfoService(
            SystemInstallationMapper installationMapper,
            Environment environment,
            AuditService auditService
    ) {
        this.installationMapper = installationMapper;
        this.environment = environment;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSnapshot current() {
        var installation = installationMapper.selectById((short) 1);
        if (installation == null) {
            throw new SystemStateUnavailableException("installation missing");
        }
        return new SystemSnapshot(
                environment.getProperty("spring.application.name", "zija"),
                environment.getProperty("info.app.version", "dev"),
                "UP",
                installation.getInstallationId(),
                installationMapper.selectDatabaseTime()
        );
    }

    @Override
    public void recordAudit(SystemApi.AuditEvent event) {
        auditService.recordAudit(event);
    }
}

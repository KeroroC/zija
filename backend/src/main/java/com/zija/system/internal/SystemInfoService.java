package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.SystemInstallationMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 系统信息服务，提供系统运行时快照查询和审计日志管理功能。
 * <p>
 * 实现 {@link SystemApi} 接口，负责返回当前系统安装信息、应用版本、数据库时间等，
 * 同时委托 {@link AuditService} 完成审计事件的记录与查询。
 */
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

    /**
     * 获取当前系统快照，包含应用名称、版本、安装 ID 和数据库时间。
     *
     * @return 系统快照信息
     * @throws SystemStateUnavailableException 如果系统安装记录缺失
     */
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

    /**
     * 记录一条审计事件。
     *
     * @param event 审计事件信息
     */
    @Override
    public void recordAudit(SystemApi.AuditEvent event) {
        auditService.recordAudit(event);
    }

    /**
     * 分页查询审计日志，支持按家庭、时间范围、操作类型、操作人和结果等条件筛选。
     *
     * @param householdId     家庭 ID（可选）
     * @param from            起始时间（可选）
     * @param to              结束时间（可选）
     * @param action          操作类型（可选）
     * @param actorAccountId  操作人账户 ID（可选）
     * @param outcome         操作结果（可选）
     * @param page            页码（从 1 开始）
     * @param pageSize        每页条数
     * @return 分页审计日志
     */
    @Override
    @Transactional(readOnly = true)
    public AuditLogPage queryAuditLogs(
            UUID householdId, OffsetDateTime from, OffsetDateTime to,
            String action, UUID actorAccountId, String outcome,
            int page, int pageSize
    ) {
        var request = new AuditLogQueryRequest(householdId, from, to, action, actorAccountId, outcome);
        var result = auditService.queryAuditLogs(request, page, pageSize);
        var items = result.getRecords().stream()
                .map(e -> new AuditLogItem(
                        e.getId(), e.getAction(), e.getOutcome(),
                        e.getActorAccountId(), e.getSubjectAccountId(),
                        e.getDetail(), e.getIpAddress(), e.getRequestId(),
                        e.getCreatedAt()
                ))
                .toList();
        return new AuditLogPage(items, result.getTotal(), page, pageSize);
    }
}

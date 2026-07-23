package com.zija.system;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 系统模块公共 API，提供应用健康快照、审计事件记录与查询能力。
 */
public interface SystemApi {

    /** 获取当前应用快照（版本、状态、安装 ID、数据库时间）。 */
    SystemSnapshot current();

    /** 记录一条审计事件。 */
    void recordAudit(SystemApi.AuditEvent event);

    /** 按条件分页查询审计日志。 */
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

    /** 应用运行快照，包含版本、状态和安装标识。 */
    record SystemSnapshot(
            String application,
            String version,
            String status,
            UUID installationId,
            OffsetDateTime databaseTime
    ) {
    }

    /** 审计事件入参，描述一次操作的行为、结果及上下文。 */
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

    /** 审计日志条目。 */
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

    /** 审计日志分页结果。 */
    record AuditLogPage(
            List<AuditLogItem> items,
            long total,
            int page,
            int pageSize
    ) {
    }
}

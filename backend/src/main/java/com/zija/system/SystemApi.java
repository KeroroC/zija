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

    /**
     * 审计动作常量。
     * <p>
     * 所有模块写审计日志时必须引用本接口常量，禁止散落动作字符串。
     */
    interface AuditAction {

        // ---------- identity ----------
        String LOGIN_SUCCESS = "LOGIN_SUCCESS";
        String LOGIN_FAILURE = "LOGIN_FAILURE";
        String LOGOUT = "LOGOUT";
        String PASSWORD_CHANGED = "PASSWORD_CHANGED";
        String DISPLAY_NAME_CHANGED = "DISPLAY_NAME_CHANGED";

        // ---------- household ----------
        String HOUSEHOLD_INITIALIZED = "HOUSEHOLD_INITIALIZED";
        String MEMBER_JOINED = "MEMBER_JOINED";
        String MEMBER_DEACTIVATED = "MEMBER_DEACTIVATED";
        String MEMBER_REACTIVATED = "MEMBER_REACTIVATED";
        String ROLE_CHANGED = "ROLE_CHANGED";
        String OWNERSHIP_TRANSFERRED = "OWNERSHIP_TRANSFERRED";
        String INVITATION_CREATED = "INVITATION_CREATED";
        String INVITATION_REDEEMED = "INVITATION_REDEEMED";
        String OWNER_RECOVERY = "OWNER_RECOVERY";

        // ---------- catalog ----------
        String CATEGORY_CREATED = "CATEGORY_CREATED";
        String CATEGORY_UPDATED = "CATEGORY_UPDATED";
        String CATEGORY_ARCHIVED = "CATEGORY_ARCHIVED";
        String CATEGORY_RESTORED = "CATEGORY_RESTORED";
        String CATEGORY_MOVED = "CATEGORY_MOVED";
        String BRAND_CREATED = "BRAND_CREATED";
        String BRAND_UPDATED = "BRAND_UPDATED";
        String BRAND_ARCHIVED = "BRAND_ARCHIVED";
        String BRAND_RESTORED = "BRAND_RESTORED";
        String UNIT_CREATED = "UNIT_CREATED";
        String UNIT_UPDATED = "UNIT_UPDATED";
        String UNIT_ARCHIVED = "UNIT_ARCHIVED";
        String UNIT_RESTORED = "UNIT_RESTORED";
        String UNIT_DECIMAL_SCALE_UPDATED = "UNIT_DECIMAL_SCALE_UPDATED";
        String TAG_CREATED = "TAG_CREATED";
        String TAG_UPDATED = "TAG_UPDATED";
        String TAG_ARCHIVED = "TAG_ARCHIVED";
        String TAG_RESTORED = "TAG_RESTORED";
        String ITEM_CREATED = "ITEM_CREATED";
        String ITEM_UPDATED = "ITEM_UPDATED";
        String ITEM_ARCHIVED = "ITEM_ARCHIVED";
        String ITEM_RESTORED = "ITEM_RESTORED";
        String ITEM_COVER_UPLOADED = "ITEM_COVER_UPLOADED";
        String ITEM_COVER_REMOVED = "ITEM_COVER_REMOVED";

        // ---------- file ----------
        String FILE_UPLOADED = "FILE_UPLOADED";
        String FILE_RENAMED = "FILE_RENAMED";
        String FILE_MOVED = "FILE_MOVED";
        String FILE_DELETED = "FILE_DELETED";
        String FILE_RESTORED = "FILE_RESTORED";
        String FILE_PURGED = "FILE_PURGED";

        // ---------- location ----------
        String LOCATION_CREATED = "LOCATION_CREATED";
        String LOCATION_RENAMED = "LOCATION_RENAMED";
        String LOCATION_MOVED = "LOCATION_MOVED";
        String LOCATION_DELETED = "LOCATION_DELETED";

        // ---------- inventory ----------
        String INVENTORY_INBOUND = "INVENTORY_INBOUND";
        String INVENTORY_CONSUME = "INVENTORY_CONSUME";
        String INVENTORY_LOSS = "INVENTORY_LOSS";
        String INVENTORY_TRANSFER = "INVENTORY_TRANSFER";
        String INVENTORY_REVERSAL = "INVENTORY_REVERSAL";
        String INVENTORY_STOCKTAKE_CONFIRM = "INVENTORY_STOCKTAKE_CONFIRM";
        String INVENTORY_STOCKTAKE_CANCEL = "INVENTORY_STOCKTAKE_CANCEL";

        // ---------- reminder ----------
        String REMINDER_RULE_UPDATE = "REMINDER_RULE_UPDATE";
        String REMINDER_TASK_SNOOZED = "REMINDER_TASK_SNOOZED";
        String REMINDER_TASK_COMPLETED = "REMINDER_TASK_COMPLETED";
        String REMINDER_TASK_IGNORED = "REMINDER_TASK_IGNORED";
        String REMINDER_TASK_REOPENED = "REMINDER_TASK_REOPENED";
        String MAIL_SETTING_UPDATE = "MAIL_SETTING_UPDATE";
        String MAIL_DIGEST_SENT = "MAIL_DIGEST_SENT";
        String MAIL_SEND_FAILED = "MAIL_SEND_FAILED";
        String REMINDER_EVENT_POISON = "REMINDER_EVENT_POISON";

        // ---------- reporting ----------
        String EXPORT_PERFORMED = "EXPORT_PERFORMED";
        String REPORTING_PROJECTION_REBUILT = "REPORTING_PROJECTION_REBUILT";
        String REPORTING_EVENT_ABANDONED = "REPORTING_EVENT_ABANDONED";

        // ---------- ai ----------
        String AI_SETTING_UPDATED = "AI_SETTING_UPDATED";
        String AI_KNOWLEDGE_SOURCE_SELECTED = "AI_KNOWLEDGE_SOURCE_SELECTED";
        String AI_KNOWLEDGE_SOURCE_CANCELLED = "AI_KNOWLEDGE_SOURCE_CANCELLED";
        String AI_KNOWLEDGE_SOURCE_RETRIED = "AI_KNOWLEDGE_SOURCE_RETRIED";
        String AI_KNOWLEDGE_REBUILD_STARTED = "AI_KNOWLEDGE_REBUILD_STARTED";
        String AI_HOUSEHOLD_QA = "AI_HOUSEHOLD_QA";
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

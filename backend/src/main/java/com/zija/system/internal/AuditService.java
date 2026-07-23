package com.zija.system.internal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.AuditLogEntity;
import com.zija.system.internal.persistence.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务，负责审计事件的持久化存储和条件查询。
 * <p>
 * 记录系统中的关键操作（如成员变动、密码修改、邀请兑换等），
 * 并支持按家庭、时间范围、操作类型等条件进行分页查询。
 */
@Service
class AuditService {

    private final AuditLogMapper auditLogMapper;

    AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 将审计事件转换为实体并持久化。
     *
     * @param event 审计事件
     */
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

    /**
     * 按条件分页查询审计日志。
     *
     * @param request  查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public IPage<AuditLogEntity> queryAuditLogs(AuditLogQueryRequest request, int page, int pageSize) {
        return auditLogMapper.queryByCondition(
                new Page<>(page, pageSize),
                request.householdId(),
                request.from(),
                request.to(),
                request.action(),
                request.actorAccountId(),
                request.outcome()
        );
    }
}

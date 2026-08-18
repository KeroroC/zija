package com.zija.ai.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.ai.internal.exception.KnowledgeSourceFormatUnsupportedException;
import com.zija.ai.internal.exception.KnowledgeSourceNotFoundException;
import com.zija.ai.internal.exception.KnowledgeSourceStateConflictException;
import com.zija.ai.internal.persistence.KnowledgeChunkMapper;
import com.zija.ai.internal.persistence.KnowledgeSourceEntity;
import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import com.zija.file.AttachmentMovedEvent;
import com.zija.file.AttachmentPurgedEvent;
import com.zija.file.AttachmentRecycledEvent;
import com.zija.file.AttachmentRestoredEvent;
import com.zija.file.FileApi;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识来源选择与生命周期：成员对附件执行选择/取消/手动重试，并跟随附件
 * 回收、恢复、改挂、永久删除事件同步派生状态。
 *
 * <p>范围规则：任何活跃成员可操作（沿用家庭附件权限）；选择后异步准备
 * （{@link KnowledgePreparationService}），本服务只维护用户可见状态。</p>
 */
@Service
class KnowledgeSourceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSourceService.class);

    private final KnowledgeSourceMapper mapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeSourceStateStore stateStore;
    private final FileApi fileApi;
    private final SystemApi systemApi;
    private final KnowledgeScopeResolver scopeResolver;

    KnowledgeSourceService(
            KnowledgeSourceMapper mapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeSourceStateStore stateStore,
            FileApi fileApi,
            SystemApi systemApi,
            KnowledgeScopeResolver scopeResolver
    ) {
        this.mapper = mapper;
        this.chunkMapper = chunkMapper;
        this.stateStore = stateStore;
        this.fileApi = fileApi;
        this.systemApi = systemApi;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 显式选择附件为知识来源：进入处理中。已选择（处理中/可用）则幂等返回当前状态；
     * 已失败或已停用则重新发起准备（重置自动重试计数并栅栏掉滞留的过期批次）。
     */
    @Transactional
    public KnowledgeSourceView select(UUID householdId, UUID actorAccountId, UUID fileId) {
        var attachment = fileApi.findAttachment(householdId, fileId)
                .orElseThrow(() -> new KnowledgeSourceNotFoundException(fileId));
        if (attachment.deletedAt() != null) {
            throw new KnowledgeSourceStateConflictException("附件在回收站中，请先恢复再选择为知识来源");
        }
        if (!KnowledgeSourceStates.SUPPORTED_MEDIA_TYPES.contains(attachment.mediaType())) {
            throw new KnowledgeSourceFormatUnsupportedException(attachment.mediaType());
        }
        OffsetDateTime now = OffsetDateTime.now();
        var existing = findByFile(householdId, fileId);
        if (existing == null) {
            var entity = new KnowledgeSourceEntity();
            // 全局 assign_uuid 对 UUID 类型字段不生效（与 file 模块一致），显式生成
            entity.setId(UUID.randomUUID());
            entity.setHouseholdId(householdId);
            entity.setFileId(fileId);
            entity.setMountType(attachment.mountType());
            entity.setMountId(attachment.mountId());
            entity.setStatus(KnowledgeSourceStates.STATUS_PROCESSING);
            entity.setAttemptCount(0);
            entity.setProcessingVersion(0);
            entity.setNextAttemptAt(now);
            entity.setSelectedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
        } else if (KnowledgeSourceStates.STATUS_FAILED.equals(existing.getStatus())
                || KnowledgeSourceStates.STATUS_DISABLED.equals(existing.getStatus())) {
            // 失败后重新选择 / 取消后重新选择：重新进入处理中（显式 SQL 清空失败信息与停用原因）
            stateStore.reactivate(existing.getId(), now);
        }
        audit(householdId, actorAccountId, SystemApi.AuditAction.AI_KNOWLEDGE_SOURCE_SELECTED, fileId);
        return view(findByFile(householdId, fileId));
    }

    /** 取消选定：变为已停用，清除分块，不参与检索。已停用则幂等返回。 */
    @Transactional
    public KnowledgeSourceView cancel(UUID householdId, UUID actorAccountId, UUID fileId) {
        var entity = requireSource(householdId, fileId);
        if (!KnowledgeSourceStates.STATUS_DISABLED.equals(entity.getStatus())) {
            stateStore.disable(entity.getId(), KnowledgeSourceStates.DISABLED_CANCELLED, OffsetDateTime.now());
            chunkMapper.deleteByAttachment(householdId, fileId);
            audit(householdId, actorAccountId, SystemApi.AuditAction.AI_KNOWLEDGE_SOURCE_CANCELLED, fileId);
        }
        return view(findByFile(householdId, fileId));
    }

    /** 手动重试失败的知识来源：重新进入处理中并重置自动重试计数。 */
    @Transactional
    public KnowledgeSourceView retry(UUID householdId, UUID actorAccountId, UUID fileId) {
        var entity = requireSource(householdId, fileId);
        switch (entity.getStatus()) {
            case KnowledgeSourceStates.STATUS_FAILED -> {
                stateStore.retry(entity.getId(), OffsetDateTime.now());
                audit(householdId, actorAccountId, SystemApi.AuditAction.AI_KNOWLEDGE_SOURCE_RETRIED, fileId);
                return view(findByFile(householdId, fileId));
            }
            case KnowledgeSourceStates.STATUS_PROCESSING ->
                    throw new KnowledgeSourceStateConflictException("知识来源正在处理中，请稍候再试");
            case KnowledgeSourceStates.STATUS_AVAILABLE ->
                    throw new KnowledgeSourceStateConflictException("知识来源已可用，无需重试");
            case KnowledgeSourceStates.STATUS_DISABLED ->
                    throw new KnowledgeSourceStateConflictException("知识来源已停用，请重新选择后再处理");
            default -> throw new IllegalStateException("未知状态: " + entity.getStatus());
        }
    }

    /** 列出当前家庭全部知识来源（按最近更新倒序）。 */
    @Transactional(readOnly = true)
    public List<KnowledgeSourceView> list(UUID householdId) {
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .eq(KnowledgeSourceEntity::getHouseholdId, householdId)
                        .orderByDesc(KnowledgeSourceEntity::getUpdatedAt))
                .stream()
                .map(this::view)
                .toList();
    }

    // ---------- 附件生命周期同步（在发布方事务内同步执行） ----------

    @EventListener
    public void onAttachmentRecycled(AttachmentRecycledEvent event) {
        var entity = findByFile(event.householdId(), event.fileId());
        if (entity == null) {
            return;
        }
        // 回收站附件立即停用并排除分块，即使物理文件仍可下载
        stateStore.disable(entity.getId(), KnowledgeSourceStates.DISABLED_RECYCLED, OffsetDateTime.now());
        chunkMapper.deleteByAttachment(event.householdId(), event.fileId());
        log.info("附件进入回收站，知识来源已停用: fileId={}", event.fileId());
    }

    @EventListener
    public void onAttachmentRestored(AttachmentRestoredEvent event) {
        var entity = findByFile(event.householdId(), event.fileId());
        if (entity == null) {
            return;
        }
        // 恢复后重新进入处理中，不自动恢复旧索引
        stateStore.reactivate(entity.getId(), OffsetDateTime.now());
        stateStore.updateMount(entity.getId(), event.mountType(), event.mountId(), OffsetDateTime.now());
        log.info("附件恢复，知识来源重新准备: fileId={}", event.fileId());
    }

    @EventListener
    public void onAttachmentMoved(AttachmentMovedEvent event) {
        var entity = findByFile(event.householdId(), event.fileId());
        if (entity == null) {
            return;
        }
        var scope = scopeResolver.resolve(event.householdId(), event.newMountType(), event.newMountId());
        stateStore.updateMount(entity.getId(), event.newMountType(), event.newMountId(), OffsetDateTime.now());
        // 改挂只改变检索范围，内容未变化：原地更新分块元数据，不重新抽取
        chunkMapper.updateScope(event.householdId(), event.fileId(),
                event.newMountType(), event.newMountId(), scope.itemId(), scope.lotId());
        log.info("附件改挂，知识来源范围已更新: fileId={} mount={}/{}",
                event.fileId(), event.newMountType(), event.newMountId());
    }

    @EventListener
    public void onAttachmentPurged(AttachmentPurgedEvent event) {
        var entity = findByFile(event.householdId(), event.fileId());
        if (entity == null) {
            return;
        }
        chunkMapper.deleteByAttachment(event.householdId(), event.fileId());
        stateStore.deleteRow(entity.getId());
        log.info("附件永久删除，知识来源派生数据已清除: fileId={}", event.fileId());
    }

    // ---------- 内部 ----------

    private KnowledgeSourceEntity requireSource(UUID householdId, UUID fileId) {
        var entity = findByFile(householdId, fileId);
        if (entity == null) {
            throw new KnowledgeSourceNotFoundException(fileId);
        }
        return entity;
    }

    private KnowledgeSourceEntity findByFile(UUID householdId, UUID fileId) {
        return mapper.selectOne(new LambdaQueryWrapper<KnowledgeSourceEntity>()
                .eq(KnowledgeSourceEntity::getHouseholdId, householdId)
                .eq(KnowledgeSourceEntity::getFileId, fileId));
    }

    private void audit(UUID householdId, UUID actorAccountId, String action, UUID fileId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, ZijaAuditOutcome.SUCCESS, householdId, actorAccountId, null, null, null,
                Map.of("fileId", fileId.toString())));
    }

    private KnowledgeSourceView view(KnowledgeSourceEntity entity) {
        // 自动重试计划仅在失败状态下对外可见（处理中的 next_attempt_at 是内部处理租约）
        OffsetDateTime nextRetryAt = KnowledgeSourceStates.STATUS_FAILED.equals(entity.getStatus())
                ? entity.getNextAttemptAt()
                : null;
        return new KnowledgeSourceView(
                entity.getFileId(),
                entity.getStatus(),
                entity.getFailureCode(),
                entity.getFailureMessage(),
                entity.getDisabledReason(),
                nextRetryAt,
                entity.getProcessingVersion(),
                entity.getSelectedAt(),
                entity.getProcessedAt(),
                entity.getUpdatedAt());
    }

    /** 知识来源对外视图（不含内部调度字段；nextRetryAt 仅供前端感知待自动重试）。 */
    record KnowledgeSourceView(
            UUID fileId,
            String status,
            String failureCode,
            String failureMessage,
            String disabledReason,
            OffsetDateTime nextRetryAt,
            Integer processingVersion,
            OffsetDateTime selectedAt,
            OffsetDateTime processedAt,
            OffsetDateTime updatedAt
    ) {
    }
}

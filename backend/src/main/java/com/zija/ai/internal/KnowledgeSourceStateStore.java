package com.zija.ai.internal;

import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 知识来源状态转换原语（独立事务，显式 SQL 落库）。
 *
 * <p>异步准备（{@link KnowledgePreparationService}）与附件生命周期监听都在自身事务外
 * 编排长流程，状态落库必须经本类的小事务完成：既保证失败标记/停用不被长流程回滚吞掉，
 * 又让并发状态判断（仅当仍为 PROCESSING 时置为可用）原子生效。所有转换都显式写出
 * {@code NULL} 列（清空失败原因 / 停止自动重试），回避 MyBatis-Plus 更新跳过 null 的默认行为。</p>
 */
@Service
class KnowledgeSourceStateStore {

    private final KnowledgeSourceMapper mapper;

    KnowledgeSourceStateStore(KnowledgeSourceMapper mapper) {
        this.mapper = mapper;
    }

    /** 标记失败并安排有限自动重试（失败次数未达上限时退避调度，否则停止自动重试）。 */
    @Transactional
    void markFailed(UUID id, OffsetDateTime now, String code, String message) {
        var entity = mapper.selectById(id);
        if (entity == null) {
            return;
        }
        int attempt = (entity.getAttemptCount() == null ? 0 : entity.getAttemptCount()) + 1;
        OffsetDateTime nextAttempt = attempt < KnowledgeSourceStates.MAX_AUTO_RETRIES
                ? now.plusSeconds(KnowledgeSourceStates.RETRY_BACKOFF_BASE_SECONDS << attempt)
                : null;
        mapper.markFailed(id, now, attempt, code, message, nextAttempt);
    }

    /** 仅当仍处于 PROCESSING 时置为 AVAILABLE；返回 0 表示并发已被取消/回收。 */
    @Transactional
    int markAvailable(UUID id, int processingVersion, OffsetDateTime now) {
        return mapper.markAvailableIfProcessing(id, processingVersion, now);
    }

    /** 停用（附件回收或成员取消）；清除处理调度。 */
    @Transactional
    void disable(UUID id, String reason, OffsetDateTime now) {
        mapper.disable(id, reason, now);
    }

    /** 手动重试：重新进入处理中并重置自动重试计数。 */
    @Transactional
    void retry(UUID id, OffsetDateTime now) {
        mapper.retry(id, now);
    }

    /** 附件恢复后重新进入处理中（不自动恢复旧索引）。 */
    @Transactional
    void reactivate(UUID id, OffsetDateTime now) {
        mapper.reactivate(id, now);
    }

    /** 更新挂载点（改挂/恢复后跟随附件当前挂载）。 */
    @Transactional
    void updateMount(UUID id, String mountType, UUID mountId, OffsetDateTime now) {
        mapper.updateMount(id, mountType, mountId, now);
    }

    /** 永久删除附件后清除知识来源选择行。 */
    @Transactional
    void deleteRow(UUID id) {
        mapper.deleteById(id);
    }
}

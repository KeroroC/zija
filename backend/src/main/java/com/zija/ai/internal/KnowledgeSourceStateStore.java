package com.zija.ai.internal;

import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 知识来源状态转换原语（{@code @Transactional(REQUIRED)}，显式 SQL 落库）。
 *
 * <p>两类调用方的事务语义不同：异步准备（{@link KnowledgePreparationService}）在
 * 自身事务外编排长流程（嵌入是长时间外部调用），这里的转换提交即生效、不被长流程
 * 回滚吞掉；附件生命周期监听（{@code @EventListener}）在发布方事务内同步执行，
 * 转换加入同一事务，随附件变更原子提交或回滚。所有转换都显式写出
 * {@code NULL} 列（清空失败原因 / 停止自动重试），回避 MyBatis-Plus 更新跳过 null
 * 的默认行为；准备路径的转换携带认领栅栏版本，过期工作者的写入被拒绝。</p>
 */
@Service
class KnowledgeSourceStateStore {

    private final KnowledgeSourceMapper mapper;

    KnowledgeSourceStateStore(KnowledgeSourceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 标记失败并安排有限自动重试（失败次数未达上限时退避调度，否则停止自动重试）。
     * 仅当仍处于 PROCESSING 且栅栏版本等于认领版本时生效：已被停用/取消或被更高
     * 版本认领接管时丢弃过期失败标记。
     */
    @Transactional
    void markFailed(UUID id, int expectedVersion, OffsetDateTime now, String code, String message) {
        var entity = mapper.selectById(id);
        if (entity == null || !KnowledgeSourceStates.STATUS_PROCESSING.equals(entity.getStatus())
                || entity.getProcessingVersion() == null || entity.getProcessingVersion() != expectedVersion) {
            return;
        }
        int attempt = (entity.getAttemptCount() == null ? 0 : entity.getAttemptCount()) + 1;
        OffsetDateTime nextAttempt = attempt < KnowledgeSourceStates.MAX_AUTO_RETRIES
                ? now.plusSeconds(KnowledgeSourceStates.RETRY_BACKOFF_BASE_SECONDS << attempt)
                : null;
        mapper.markFailed(id, expectedVersion, now, attempt, code, message, nextAttempt);
    }

    /** 仅当仍处于 PROCESSING 且栅栏版本匹配时置为 AVAILABLE；返回 0 表示并发已被取消/回收或认领被接管。 */
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

    /** 改挂时更新范围，并栅栏掉仍携带旧范围的处理中工作者。 */
    @Transactional
    void remount(UUID id, String mountType, UUID mountId, OffsetDateTime now) {
        mapper.remount(id, mountType, mountId, now);
    }

    /** 永久删除附件后清除知识来源选择行。 */
    @Transactional
    void deleteRow(UUID id) {
        mapper.deleteById(id);
    }
}

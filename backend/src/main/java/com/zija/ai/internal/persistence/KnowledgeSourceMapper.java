package com.zija.ai.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识来源持久化。认领（claim）使用带 {@code FOR UPDATE SKIP LOCKED} 的单条
 * {@code UPDATE ... RETURNING}，避免多实例/多线程重复处理同一知识来源。
 *
 * <p>状态转换一律用显式 UPDATE：MyBatis-Plus 的 {@code updateById} 默认跳过 null 字段，
 * 而「清空失败原因 / 停止自动重试」正是要把列写回 NULL，必须逐列显式赋值。</p>
 */
@Mapper
public interface KnowledgeSourceMapper extends BaseMapper<KnowledgeSourceEntity> {

    /**
     * 认领到期可处理的知识来源（PROCESSING 或 FAILED 且 {@code next_attempt_at} 已到）：
     * 状态置回 PROCESSING、处理租约顺延、栅栏版本自增，返回被认领行的 id 与新版本号。
     */
    List<ClaimedKnowledgeSource> claimDue(
            @Param("now") OffsetDateTime now,
            @Param("leaseUntil") OffsetDateTime leaseUntil,
            @Param("limit") int limit
    );

    /**
     * 仅当仍处于 PROCESSING 且栅栏版本等于认领版本时标记为可用
     * （防止与回收/取消并发时复活已停用来源，或被更高版本认领接管后重复翻转）。
     */
    int markAvailableIfProcessing(
            @Param("id") UUID id,
            @Param("processingVersion") int processingVersion,
            @Param("processedAt") OffsetDateTime processedAt
    );

    /**
     * 标记失败：记录失败原因/次数，并安排（或停止）自动重试。
     * 仅当仍处于 PROCESSING 且栅栏版本等于认领版本时生效，过期工作者的失败标记被丢弃。
     */
    @Update("""
            UPDATE ai_knowledge_source
            SET status = 'FAILED',
                attempt_count = #{attempt},
                failure_code = #{code},
                failure_message = #{message},
                next_attempt_at = #{nextAttemptAt},
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND processing_version = #{expectedVersion}
            """)
    int markFailed(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion,
            @Param("now") OffsetDateTime now,
            @Param("attempt") int attempt,
            @Param("code") String code,
            @Param("message") String message,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt
    );

    /** 停用（成员取消或附件回收）：清除处理调度与失败信息。 */
    @Update("""
            UPDATE ai_knowledge_source
            SET status = 'DISABLED',
                disabled_reason = #{reason},
                next_attempt_at = NULL,
                failure_code = NULL,
                failure_message = NULL,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int disable(@Param("id") UUID id, @Param("reason") String reason, @Param("now") OffsetDateTime now);

    /**
     * 手动重试：重新进入处理中并重置自动重试计数。栅栏版本自增，
     * 使重试前滞留的过期处理批次全部失效。
     */
    @Update("""
            UPDATE ai_knowledge_source
            SET status = 'PROCESSING',
                next_attempt_at = #{now},
                attempt_count = 0,
                failure_code = NULL,
                failure_message = NULL,
                processing_version = processing_version + 1,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int retry(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /**
     * 附件恢复后重新进入处理中（不自动恢复旧索引）。栅栏版本自增，
     * 使回收前滞留的过期处理批次全部失效。
     */
    @Update("""
            UPDATE ai_knowledge_source
            SET status = 'PROCESSING',
                next_attempt_at = #{now},
                attempt_count = 0,
                failure_code = NULL,
                failure_message = NULL,
                disabled_reason = NULL,
                processing_version = processing_version + 1,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int reactivate(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /** 更新挂载点（改挂/恢复后跟随附件当前挂载）。 */
    @Update("""
            UPDATE ai_knowledge_source
            SET mount_type = #{mountType},
                mount_id = #{mountId},
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateMount(
            @Param("id") UUID id,
            @Param("mountType") String mountType,
            @Param("mountId") UUID mountId,
            @Param("now") OffsetDateTime now
    );
}

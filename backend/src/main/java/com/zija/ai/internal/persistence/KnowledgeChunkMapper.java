package com.zija.ai.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * ai_knowledge_chunk 的直接管理（ai 模块自有表）：按附件清理派生分块、
 * 处理完成后把分块 readiness_status 从 PROCESSING 翻转为 AVAILABLE。
 * 向量检索仍走 Spring AI VectorStore 边界（{@code AiKnowledgeVectorStore}）。
 *
 * <p>SQL 见 {@code mapper/ai/KnowledgeChunkMapper.xml}。表内 UUID 定位列是
 * metadata 派生生成列（varchar 语义），绑定 UUID 实参时 PostgreSQL 没有
 * varchar = uuid 操作符，必须显式 {@code ::text} 转换。</p>
 *
 * <p>准备路径的分块变更（清理、翻转可用）带栅栏版本条件：租约到期被更高版本
 * 认领接管后，过期工作者的写入落空，不会误删现任认领者的分块；生命周期路径
 * （回收/取消/永久删除）的用户意图优先，走无条件清理。</p>
 */
@Mapper
public interface KnowledgeChunkMapper {

    /** 删除某家庭某附件的全部分块（回收/取消/永久删除等生命周期清理，无条件生效）。 */
    int deleteByAttachment(@Param("householdId") UUID householdId, @Param("fileId") UUID fileId);

    /**
     * 准备路径的分块清理：仅当来源仍处于 PROCESSING 且栅栏版本等于认领版本时生效，
     * 过期工作者不得删除现任认领者的分块。
     */
    int deleteByAttachmentIfCurrent(
            @Param("householdId") UUID householdId,
            @Param("fileId") UUID fileId,
            @Param("sourceId") UUID sourceId,
            @Param("processingVersion") int processingVersion
    );

    /**
     * 准备路径的可用翻转（JSONB 元数据翻转，生成列自动跟随）：仅当来源仍处于
     * PROCESSING 且栅栏版本等于认领版本时生效，过期批次不得变为可检索。
     */
    int markAllAvailableIfCurrent(
            @Param("householdId") UUID householdId,
            @Param("fileId") UUID fileId,
            @Param("sourceId") UUID sourceId,
            @Param("processingVersion") int processingVersion
    );

    /** 改挂后原地更新分块检索范围元数据（挂载点与物品/批次定位）。 */
    int updateScope(
            @Param("householdId") UUID householdId,
            @Param("fileId") UUID fileId,
            @Param("mountType") String mountType,
            @Param("mountId") UUID mountId,
            @Param("itemId") UUID itemId,
            @Param("lotId") UUID lotId
    );
}

package com.zija.ai.internal.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * ai_knowledge_chunk 的直接管理（ai 模块自有表）：按附件清理派生分块、
 * 处理完成后把分块 readiness_status 从 PROCESSING 翻转为 AVAILABLE。
 * 向量检索仍走 Spring AI VectorStore 边界（{@code AiKnowledgeVectorStore}）。
 *
 * <p>表内 UUID 定位列是 metadata 派生生成列（varchar 语义），绑定 UUID 实参时
 * PostgreSQL 没有 varchar = uuid 操作符，必须显式 {@code ::text} 转换。</p>
 */
@Mapper
public interface KnowledgeChunkMapper {

    /** 删除某家庭某附件的全部分块（回收/取消/永久删除/重新准备前的清理）。 */
    @Delete("""
            DELETE FROM ai_knowledge_chunk
            WHERE household_id = #{householdId}
              AND attachment_id = #{fileId}::text
            """)
    int deleteByAttachment(@Param("householdId") UUID householdId, @Param("fileId") UUID fileId);

    /** 把某附件全部分块标记为可用（JSONB 元数据翻转，生成列自动跟随）。 */
    @Update("""
            UPDATE ai_knowledge_chunk
            SET metadata = metadata || '{"readiness_status":"AVAILABLE"}',
                updated_at = CURRENT_TIMESTAMP
            WHERE household_id = #{householdId}
              AND attachment_id = #{fileId}::text
            """)
    int markAllAvailable(@Param("householdId") UUID householdId, @Param("fileId") UUID fileId);

    /** 改挂后原地更新分块检索范围元数据（挂载点与物品/批次定位）。 */
    @Update("""
            UPDATE ai_knowledge_chunk
            SET metadata = metadata || jsonb_build_object(
                    'mount_type', #{mountType},
                    'mount_id', #{mountId}::text,
                    'item_id', #{itemId}::text,
                    'lot_id', #{lotId}::text
                ),
                updated_at = CURRENT_TIMESTAMP
            WHERE household_id = #{householdId}
              AND attachment_id = #{fileId}::text
            """)
    int updateScope(
            @Param("householdId") UUID householdId,
            @Param("fileId") UUID fileId,
            @Param("mountType") String mountType,
            @Param("mountId") UUID mountId,
            @Param("itemId") UUID itemId,
            @Param("lotId") UUID lotId
    );
}

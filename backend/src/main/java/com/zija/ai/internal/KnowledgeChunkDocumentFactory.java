package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeChunker.Chunk;
import com.zija.ai.internal.persistence.KnowledgeSourceEntity;
import com.zija.file.FileApi;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把抽取分块组装为可写入 pgvector 的文档：服务端生成全部过滤与引用元数据，
 * 家庭、挂载点、物品/批次、附件和准备状态不由模型请求覆盖。
 *
 * <p>分块先以 {@code PROCESSING} 状态写入，全部写入成功后再整体翻转为
 * {@code AVAILABLE}，避免失败时残留可检索的半个附件。嵌入模型名取自
 * {@link AiModelNames}，与探活/状态报告同一来源。</p>
 */
@Component
class KnowledgeChunkDocumentFactory {

    /** 分块算法版本（随算法变更递增；与知识来源 processing_version 区分）。 */
    static final String CHUNKER_VERSION = "1";

    static final String READINESS_PROCESSING = "PROCESSING";

    private final AiModelNames modelNames;

    KnowledgeChunkDocumentFactory(AiModelNames modelNames) {
        this.modelNames = modelNames;
    }

    List<Document> build(
            FileApi.AttachmentInfo attachment,
            KnowledgeSourceEntity source,
            KnowledgeScopeResolver.Scope scope,
            List<Chunk> chunks,
            int processingVersion
    ) {
        List<Document> documents = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("household_id", attachment.householdId().toString());
            metadata.put("mount_type", source.getMountType());
            metadata.put("mount_id", source.getMountId().toString());
            if (scope.itemId() != null) {
                metadata.put("item_id", scope.itemId().toString());
            }
            if (scope.lotId() != null) {
                metadata.put("lot_id", scope.lotId().toString());
            }
            metadata.put("attachment_id", attachment.id().toString());
            metadata.put("readiness_status", READINESS_PROCESSING);
            if (chunk.pageNumber() != null) {
                metadata.put("page_number", chunk.pageNumber());
            }
            if (chunk.sectionPath() != null) {
                metadata.put("section_path", chunk.sectionPath());
            }
            metadata.put("char_start", chunk.charStart());
            metadata.put("char_end", chunk.charEnd());
            metadata.put("embedding_model", modelNames.embeddingModel());
            metadata.put("embedding_dimensions", AiService.EMBEDDING_DIMENSIONS);
            metadata.put("chunker_version", CHUNKER_VERSION);
            metadata.put("processing_version", processingVersion);
            documents.add(new Document(UUID.randomUUID().toString(), chunk.text(), metadata));
        }
        return documents;
    }
}

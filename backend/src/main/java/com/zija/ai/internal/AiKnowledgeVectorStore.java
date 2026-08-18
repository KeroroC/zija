package com.zija.ai.internal;

import com.zija.file.FileApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Server-owned pgvector boundary that always applies household and knowledge-scope filters. */
@Service
class AiKnowledgeVectorStore {

    private static final String AVAILABLE = "AVAILABLE";

    private final VectorStore vectorStore;

    AiKnowledgeVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    void add(List<Document> documents) {
        vectorStore.add(documents);
    }

    List<Document> search(SearchScope scope, String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100");
        }
        var filter = new FilterExpressionBuilder();
        var household = filter.eq("household_id", scope.householdId().toString());
        var mountType = filter.eq("mount_type", scope.mountType());
        var mountId = filter.eq("mount_id", scope.mountId().toString());
        var attachment = filter.eq("attachment_id", scope.attachmentId());
        var available = filter.eq("readiness_status", AVAILABLE);
        var expression = filter.and(
                filter.and(household, mountType),
                filter.and(mountId, filter.and(attachment, available)));
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .filterExpression(expression.build())
                .build());
    }

    /**
     * 知识问答检索：家庭、回答范围、附件白名单与可用状态全部由服务端构造。
     */
    List<Document> search(KnowledgeSearchScope scope, String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100");
        }
        if (scope.attachmentIds().isEmpty()) {
            return List.of();
        }

        var filter = new FilterExpressionBuilder();
        var household = filter.eq("household_id", scope.householdId().toString());
        var available = filter.eq("readiness_status", AVAILABLE);
        var attachments = filter.in("attachment_id", scope.attachmentIds().stream()
                .map(UUID::toString)
                .map(value -> (Object) value)
                .toList());

        var householdMount = filter.and(
                filter.eq("mount_type", FileApi.MOUNT_HOUSEHOLD),
                filter.eq("mount_id", scope.householdId().toString()));
        var itemMount = filter.and(
                filter.and(filter.eq("mount_type", FileApi.MOUNT_ITEM),
                        filter.eq("mount_id", scope.itemId().toString())),
                filter.eq("item_id", scope.itemId().toString()));
        FilterExpressionBuilder.Op mounts = filter.or(householdMount, itemMount);
        if (scope.lotId() != null) {
            var lotMount = filter.and(
                    filter.and(filter.eq("mount_type", FileApi.MOUNT_LOT),
                            filter.eq("mount_id", scope.lotId().toString())),
                    filter.eq("lot_id", scope.lotId().toString()));
            mounts = filter.or(mounts, lotMount);
        }

        var expression = filter.and(
                filter.and(household, available),
                filter.and(attachments, filter.group(mounts)));
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .filterExpression(expression.build())
                .build());
    }

    void delete(List<String> documentIds) {
        vectorStore.delete(List.copyOf(documentIds));
    }

    record SearchScope(UUID householdId, String mountType, UUID mountId, String attachmentId) {
        SearchScope {
            if (householdId == null || mountId == null) {
                throw new IllegalArgumentException("householdId and mountId are required");
            }
            if (mountType == null || mountType.isBlank()) {
                throw new IllegalArgumentException("mountType must not be blank");
            }
            if (attachmentId == null || attachmentId.isBlank()) {
                throw new IllegalArgumentException("attachmentId must not be blank");
            }
        }
    }

    record KnowledgeSearchScope(
            UUID householdId,
            UUID itemId,
            UUID lotId,
            List<UUID> attachmentIds
    ) {
        KnowledgeSearchScope {
            if (householdId == null || itemId == null) {
                throw new IllegalArgumentException("householdId and itemId are required");
            }
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
        }
    }
}

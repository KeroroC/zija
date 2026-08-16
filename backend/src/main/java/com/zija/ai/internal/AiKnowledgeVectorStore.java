package com.zija.ai.internal;

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
}

package com.zija.ai.internal;

import com.zija.file.FileApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-owned pgvector boundary that always applies household and knowledge-scope filters. */
@Service
class AiKnowledgeVectorStore {

    private static final String AVAILABLE = "AVAILABLE";

    private final VectorStore vectorStore;
    private final VectorStore queryVectorStore;
    private final SuppliedQueryEmbeddingModel queryEmbeddingModel;

    AiKnowledgeVectorStore(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            PgVectorStoreProperties properties
    ) {
        this.vectorStore = vectorStore;
        this.queryEmbeddingModel = new SuppliedQueryEmbeddingModel();
        this.queryVectorStore = PgVectorStore.builder(jdbcTemplate, queryEmbeddingModel)
                .schemaName(properties.getSchemaName())
                .idType(properties.getIdType())
                .vectorTableName(properties.getTableName())
                .dimensions(properties.getDimensions())
                .distanceType(properties.getDistanceType())
                .indexType(properties.getIndexType())
                .initializeSchema(false)
                .vectorTableValidationsEnabled(false)
                .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                .build();
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
        return search(vectorStore, scope, query, topK);
    }

    List<Document> search(
            KnowledgeSearchScope scope,
            String query,
            float[] queryEmbedding,
            int topK
    ) {
        if (queryEmbedding == null || queryEmbedding.length != AiService.EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException("query embedding must contain 1024 dimensions");
        }
        return queryEmbeddingModel.withEmbedding(
                queryEmbedding,
                () -> search(queryVectorStore, scope, query, topK));
    }

    private List<Document> search(
            VectorStore store,
            KnowledgeSearchScope scope,
            String query,
            int topK
    ) {
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
        return store.similaritySearch(SearchRequest.builder()
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

    private static final class SuppliedQueryEmbeddingModel implements EmbeddingModel {

        private final ThreadLocal<float[]> currentEmbedding = new ThreadLocal<>();

        <T> T withEmbedding(float[] embedding, Supplier<T> action) {
            if (currentEmbedding.get() != null) {
                throw new IllegalStateException("query embedding is already set for this thread");
            }
            currentEmbedding.set(embedding.clone());
            try {
                return action.get();
            } finally {
                currentEmbedding.remove();
            }
        }

        @Override
        public float[] embed(String text) {
            float[] embedding = currentEmbedding.get();
            if (embedding == null) {
                throw new IllegalStateException("query embedding is not set");
            }
            return embedding.clone();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            float[] embedding = currentEmbedding.get();
            if (embedding == null) {
                throw new IllegalStateException("query embedding is not set");
            }
            return new EmbeddingResponse(request.getInstructions().stream()
                    .map(ignored -> new Embedding(embedding.clone(), 0, EmbeddingResultMetadata.EMPTY))
                    .toList());
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public int dimensions() {
            return AiService.EMBEDDING_DIMENSIONS;
        }
    }
}

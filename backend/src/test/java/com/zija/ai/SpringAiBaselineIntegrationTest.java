package com.zija.ai.internal;

import com.zija.SharedPostgres;
import com.zija.TestDb;
import com.zija.ai.AiApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.vectorstore.pgvector.initialize-schema=false",
        "spring.ai.vectorstore.pgvector.schema-validation=true",
        "spring.ai.vectorstore.pgvector.table-name=ai_knowledge_chunk",
        "spring.ai.vectorstore.pgvector.dimensions=768"
})
@Import(SpringAiBaselineIntegrationTest.TestEmbeddingConfiguration.class)
class SpringAiBaselineIntegrationTest {

    private static final UUID HOUSEHOLD_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOUSEHOLD_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MOUNT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MOUNT_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ITEM_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID LOT_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID LOT_B = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        registry.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        registry.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private AiApi aiApi;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AiKnowledgeVectorStore knowledgeVectorStore;

    @BeforeEach
    void cleanDatabase() {
        TestDb.cleanAll(jdbc);
        jdbc.update("""
                INSERT INTO ai_provider_setting(singleton_key, enabled, provider_id)
                VALUES (1, TRUE, 'ollama')
                """);
    }

    @Test
    void exposesProviderThroughProjectOwnedBoundary() {
        assertThat(aiApi.providerId()).isEqualTo("ollama");
        List<float[]> vectors = aiApi.embed(new AiApi.EmbeddingRequest(List.of("test embedding"))).vectors();
        assertThat(vectors).hasSize(1);
        assertThat(vectors.getFirst()).hasSize(768);
    }

    @Test
    void startsConfiguredChatClient() {
        assertThat(chatClientBuilder.build()).isNotNull();
    }

    @Test
    void startsChatClientAndDispatchesReadOnlyToolsWithoutRemoteModel() {
        ReadOnlyTools tools = new ReadOnlyTools();
        ToolCallingChatModel model = new ToolCallingChatModel();

        String answer = ChatClient.builder(model)
                .defaultTools(tools)
                .build()
                .prompt("Where is the item?")
                .call()
                .content();

        assertThat(answer).isEqualTo("tool completed");
        assertThat(model.toolDefinitionRegistered).isTrue();
        assertThat(model.toolResponseReceived).isTrue();
        assertThat(tools.lastQuestion).isEqualTo("Where is the item?");
    }

    @Test
    void supportsFilteredVectorLifecycleOnAutoconfiguredFlywayManagedTable() {
        assertThat(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')", Boolean.class))
                .isTrue();
        assertThat(vectorStore).isInstanceOf(PgVectorStore.class);
        assertThat(((PgVectorStore) vectorStore).getDistanceType())
                .isEqualTo(PgVectorStore.PgDistanceType.COSINE_DISTANCE);

        Document householdDocument = document("pine instructions", HOUSEHOLD_A, MOUNT_A, ITEM_A, LOT_A,
                "attachment-a", "AVAILABLE");
        Document sameHouseholdDifferentScopeDocument = document("pine instructions", HOUSEHOLD_A, MOUNT_B, ITEM_B,
                LOT_B, "attachment-b", "AVAILABLE");
        Document otherHouseholdDocument = document("pine instructions", HOUSEHOLD_B, MOUNT_B, ITEM_B, LOT_B,
                "attachment-b", "DISABLED");
        knowledgeVectorStore.add(List.of(householdDocument, sameHouseholdDifferentScopeDocument,
                otherHouseholdDocument));

        Map<String, Object> generatedMetadata = jdbc.queryForMap(
                "SELECT household_id, mount_type, mount_id, item_id, lot_id, attachment_id, readiness_status, "
                        + "page_number, section_path, char_start, char_end FROM ai_knowledge_chunk WHERE id = ?",
                UUID.fromString(householdDocument.getId()));
        assertThat(generatedMetadata)
                .containsEntry("household_id", HOUSEHOLD_A)
                .containsEntry("mount_type", "ITEM")
                .containsEntry("mount_id", MOUNT_A)
                .containsEntry("item_id", ITEM_A)
                .containsEntry("lot_id", LOT_A)
                .containsEntry("attachment_id", "attachment-a")
                .containsEntry("readiness_status", "AVAILABLE")
                .containsEntry("page_number", 4)
                .containsEntry("section_path", "care/cleaning")
                .containsEntry("char_start", 10)
                .containsEntry("char_end", 26);

        List<Document> results = knowledgeVectorStore.search(
                new AiKnowledgeVectorStore.SearchScope(
                        HOUSEHOLD_A, "ITEM", MOUNT_A, "attachment-a"),
                "pine instructions", 10);

        assertThat(results).extracting(Document::getId).containsExactly(householdDocument.getId());
        assertThat(results.getFirst().getMetadata())
                .containsEntry("attachment_id", "attachment-a")
                .containsEntry("page_number", 4);

        knowledgeVectorStore.delete(List.of(householdDocument.getId()));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_chunk WHERE id = ?", Integer.class,
                UUID.fromString(householdDocument.getId())))
                .isZero();
    }

    private static Document document(
            String text,
            UUID householdId,
            UUID mountId,
            UUID itemId,
            UUID lotId,
            String attachmentId,
            String readinessStatus
    ) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(Map.ofEntries(
                        Map.entry("household_id", householdId.toString()),
                        Map.entry("mount_type", "ITEM"),
                        Map.entry("mount_id", mountId.toString()),
                        Map.entry("item_id", itemId.toString()),
                        Map.entry("lot_id", lotId.toString()),
                        Map.entry("attachment_id", attachmentId),
                        Map.entry("readiness_status", readinessStatus),
                        Map.entry("page_number", 4),
                        Map.entry("section_path", "care/cleaning"),
                        Map.entry("char_start", 10),
                        Map.entry("char_end", 26),
                        Map.entry("embedding_model", "test-embedding"),
                        Map.entry("embedding_dimensions", 768),
                        Map.entry("chunker_version", "v1")))
                .build();
    }

    static final class ReadOnlyTools {
        private String lastQuestion;

        @org.springframework.ai.tool.annotation.Tool(description = "Read current household facts")
        public String householdFacts(String question) {
            lastQuestion = question;
            return "read-only test tool: " + question;
        }
    }

    static final class ToolCallingChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();
        private boolean toolDefinitionRegistered;
        private boolean toolResponseReceived;

        @Override
        public ChatResponse call(Prompt prompt) {
            if (calls.getAndIncrement() == 0) {
                if (prompt.getOptions() instanceof ToolCallingChatOptions options
                        && options.getToolCallbacks() != null) {
                    toolDefinitionRegistered = options.getToolCallbacks().stream()
                            .anyMatch(callback -> callback.getToolDefinition().name().equals("householdFacts"));
                }
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder()
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "tool-call-1",
                                        "function",
                                        "householdFacts",
                                        "{\"question\":\"Where is the item?\"}")))
                                .build())));
            }

            toolResponseReceived = prompt.getInstructions().stream()
                    .anyMatch(ToolResponseMessage.class::isInstance);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("tool completed"))));
        }

        @Override
        public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEmbeddingConfiguration {
        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }
    }

    static final class DeterministicEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(vector(), 0, EmbeddingResultMetadata.EMPTY))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector();
        }

        @Override
        public int dimensions() {
            return 768;
        }

        private static float[] vector() {
            float[] vector = new float[768];
            vector[0] = 1.0f;
            return vector;
        }
    }
}

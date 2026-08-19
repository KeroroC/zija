package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.ai.AiApi;
import com.zija.file.FileApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 知识来源问答的受认证 HTTP seam 集成测试。 */
@AutoConfigureMockMvc
@Import(KnowledgeQaEndpointIntegrationTest.FakeAiSeam.class)
class KnowledgeQaEndpointIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID UNIT_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID LOT_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID FILE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID HOUSEHOLD_FILE_ID = UUID.fromString("51000000-0000-0000-0000-000000000002");
    private static final UUID HOUSEHOLD_SOURCE_ID = UUID.fromString("61000000-0000-0000-0000-000000000002");
    private static final UUID LOT_FILE_ID = UUID.fromString("51000000-0000-0000-0000-000000000003");
    private static final UUID LOT_SOURCE_ID = UUID.fromString("61000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiKnowledgeVectorStore vectorStore;

    @Autowired
    private CapturingChatModel chatModel;

    @Autowired
    private DeterministicEmbeddingModel embeddingModel;

    @Autowired
    private FileApi fileApi;

    @Autowired
    private KnowledgePreparationService preparationService;

    @MockitoBean
    private ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        jdbc.update("""
                INSERT INTO ai_provider_setting(singleton_key, enabled, provider_id)
                VALUES (1, TRUE, 'deterministic')
                """);
        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (1, ?, '测试家庭', 'Asia/Shanghai')
                """, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO account(id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'owner', 'OWNER', '{bcrypt}test', '户主', 'ACTIVE')
                """, OWNER_ACCOUNT_ID);
        jdbc.update("""
                INSERT INTO member(id, household_id, account_id, role, status)
                VALUES (?, ?, ?, 'OWNER', 'ACTIVE')
                """, UUID.randomUUID(), HOUSEHOLD_ID, OWNER_ACCOUNT_ID);
        jdbc.update("""
                INSERT INTO catalog_unit(id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, '台', '台', 0, 'ACTIVE')
                """, UNIT_ID, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '咖啡机', 'DURABLE', ?, 'ACTIVE', 1)
                """, ITEM_ID, HOUSEHOLD_ID, UNIT_ID);
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, lot_number, version)
                VALUES (?, ?, ?, 'LOT-COFFEE-01', 1)
                """, LOT_ID, HOUSEHOLD_ID, ITEM_ID);
        insertAvailableItemAttachment(FILE_ID, SOURCE_ID, "咖啡机说明书.pdf", ITEM_ID);

        vectorStore.add(List.of(document(
                "清洁时先取下滤网，用温水冲洗并完全晾干后装回。",
                HOUSEHOLD_ID, "ITEM", ITEM_ID, ITEM_ID, null, FILE_ID,
                12, "维护/滤网清洁", 120, 148)));
        chatModel.reset("先取下滤网，用温水冲洗，完全晾干后再装回。");
        embeddingModel.reset();
    }

    @Test
    void memberGetsGroundedItemAnswerWithLocatableAttachmentEvidence() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机滤网怎么清洁？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.summary").value("先取下滤网，用温水冲洗，完全晾干后再装回。"))
                .andExpect(jsonPath("$.sources[0].category").value("KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.sources[0].attachmentId").value(FILE_ID.toString()))
                .andExpect(jsonPath("$.sources[0].attachmentName").value("咖啡机说明书.pdf"))
                .andExpect(jsonPath("$.sources[0].attachmentUrl")
                        .value("/api/v1/files/" + FILE_ID + "/content"))
                .andExpect(jsonPath("$.sources[0].mountType").value("ITEM"))
                .andExpect(jsonPath("$.sources[0].mountId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.sources[0].mountLabel").value("咖啡机"))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(12))
                .andExpect(jsonPath("$.sources[0].sectionPath").value("维护/滤网清洁"))
                .andExpect(jsonPath("$.sources[0].excerpt")
                        .value("清洁时先取下滤网，用温水冲洗并完全晾干后装回。"))
                .andExpect(jsonPath("$.sources[0].charStart").value(120))
                .andExpect(jsonPath("$.sources[0].charEnd").value(148));

        assertThat(chatModel.lastPrompt()).contains("咖啡机滤网怎么清洁？", "清洁时先取下滤网");
    }

    @Test
    void mixedAnswerKeepsFactAndKnowledgePartsSeparateAndMarksTheirConflict() throws Exception {
        vectorStore.add(List.of(document(
                "说明书记录当前库存 3 台。",
                HOUSEHOLD_ID, "ITEM", ITEM_ID, ITEM_ID, null, FILE_ID,
                20, "旧库存记录", 200, 214)));
        chatModel.reset(
                "家庭事实显示当前库存 0 台。",
                "知识来源记录当前库存 3 台。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机当前库存和说明书记录一致吗？",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.usedAnswerScope").value("BOTH"))
                .andExpect(jsonPath("$.answerParts.length()").value(2))
                .andExpect(jsonPath("$.answerParts[*].category",
                        org.hamcrest.Matchers.containsInAnyOrder("HOUSEHOLD_FACT", "KNOWLEDGE_SOURCE")))
                .andExpect(jsonPath("$.sources[*].category",
                        org.hamcrest.Matchers.hasItems("HOUSEHOLD_FACT", "KNOWLEDGE_SOURCE")))
                .andExpect(jsonPath("$.conflicts[0].kind").value("QUANTITY"))
                .andExpect(jsonPath("$.conflicts[0].factValue").value("0"))
                .andExpect(jsonPath("$.conflicts[0].knowledgeValue").value("3"))
                .andExpect(jsonPath("$.conflicts[0].note").value(
                        org.hamcrest.Matchers.containsString("不一致")));
    }

    @Test
    void mixedAnswerMarksLocationConflictWhenBothSourcesNameOneLocation() throws Exception {
        vectorStore.add(List.of(document(
                "设备的存放位置是厨房。",
                HOUSEHOLD_ID, "ITEM", ITEM_ID, ITEM_ID, null, FILE_ID,
                21, "存放要求", 215, 226)));
        chatModel.reset(
                "家庭事实显示存放位置是客厅。",
                "知识来源记录存放位置是厨房。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机放在哪里，说明书怎么说？",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts[0].kind").value("LOCATION"))
                .andExpect(jsonPath("$.conflicts[0].factValue").value("客厅"))
                .andExpect(jsonPath("$.conflicts[0].knowledgeValue").value("厨房"));
    }

    @Test
    void mixedAnswerDoesNotTreatUnrelatedDatesAsAConflict() throws Exception {
        vectorStore.add(List.of(document(
                "说明书发布日期是 2025-01-01，记录当前库存 3 台。",
                HOUSEHOLD_ID, "ITEM", ITEM_ID, ITEM_ID, null, FILE_ID,
                22, "文档信息", 227, 254)));
        chatModel.reset(
                "家庭事实显示当前库存 3 台，数据时间是 2026-08-19。",
                "知识来源记录当前库存 3 台，说明书发布日期是 2025-01-01。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机当前库存和说明书记录一致吗？",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts").isEmpty());
    }

    @Test
    void knowledgeQuestionIgnoresAmbiguousLocationNamesWhenTheItemIsUnambiguous() throws Exception {
        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '厨房', '厨房', 0, false, 0),
                       (?, ?, NULL, '客厅', '客厅', 1, false, 0)
                """, firstRoomId, HOUSEHOLD_ID, secondRoomId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, '柜子', '柜子', 0, false, 0),
                       (?, ?, ?, '柜子', '柜子', 0, false, 0)
                """, UUID.randomUUID(), HOUSEHOLD_ID, firstRoomId,
                UUID.randomUUID(), HOUSEHOLD_ID, secondRoomId);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机放在柜子里时怎么维护？",
                                  "answerScope": "KNOWLEDGE_SOURCE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.targetScope.type").value("ITEM"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.candidates").isEmpty());
    }

    @Test
    void mixedQuestionContinuesAfterConfirmingAnAmbiguousLocation() throws Exception {
        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        UUID firstCabinetId = UUID.randomUUID();
        UUID secondCabinetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '厨房', '厨房', 0, false, 0),
                       (?, ?, NULL, '客厅', '客厅', 1, false, 0)
                """, firstRoomId, HOUSEHOLD_ID, secondRoomId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, '柜子', '柜子', 0, false, 0),
                       (?, ?, ?, '柜子', '柜子', 0, false, 0)
                """, firstCabinetId, HOUSEHOLD_ID, firstRoomId,
                secondCabinetId, HOUSEHOLD_ID, secondRoomId);
        chatModel.reset(
                "家庭事实显示柜子中没有咖啡机库存。",
                "知识来源说明咖啡机应定期清洁滤网。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机在柜子里的库存和说明书维护要求是什么？",
                                  "answerScope": "BOTH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LOCATION"))));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机在柜子里的库存和说明书维护要求是什么？",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "LOCATION", "id": "%s"}
                                }
                                """.formatted(firstCabinetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.targetScope.type").value("LOCATION"))
                .andExpect(jsonPath("$.targetScope.id").value(firstCabinetId.toString()))
                .andExpect(jsonPath("$.answerParts.length()").value(2));
    }

    @Test
    void mixedQuestionConfirmsItemAndLocationAmbiguitiesInSequence() throws Exception {
        UUID secondItemId = UUID.randomUUID();
        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        UUID firstCabinetId = UUID.randomUUID();
        UUID secondCabinetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '咖啡机', 'DURABLE', ?, 'ACTIVE', 1)
                """, secondItemId, HOUSEHOLD_ID, UNIT_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '厨房', '厨房', 0, false, 0),
                       (?, ?, NULL, '客厅', '客厅', 1, false, 0)
                """, firstRoomId, HOUSEHOLD_ID, secondRoomId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, '柜子', '柜子', 0, false, 0),
                       (?, ?, ?, '柜子', '柜子', 0, false, 0)
                """, firstCabinetId, HOUSEHOLD_ID, firstRoomId,
                secondCabinetId, HOUSEHOLD_ID, secondRoomId);
        chatModel.reset(
                "家庭事实显示柜子中没有咖啡机库存。",
                "知识来源说明咖啡机应定期清洁滤网。");

        String question = "咖啡机在柜子里的库存和说明书维护要求是什么？";
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "answerScope": "BOTH"
                                }
                                """.formatted(question)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ITEM"))));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(question, ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LOCATION"))));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "LOCATION", "id": "%s"},
                                  "confirmedScopes": [{"type": "ITEM", "id": "%s"}]
                                }
                                """.formatted(question, firstCabinetId, ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.targetScope.type").value("LOCATION"))
                .andExpect(jsonPath("$.targetScope.id").value(firstCabinetId.toString()))
                .andExpect(jsonPath("$.answerParts.length()").value(2));
    }

    @Test
    void mixedQuestionKeepsItemPageContextAfterConfirmingAnAmbiguousLocation() throws Exception {
        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        UUID firstCabinetId = UUID.randomUUID();
        UUID secondCabinetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '厨房', '厨房', 0, false, 0),
                       (?, ?, NULL, '客厅', '客厅', 1, false, 0)
                """, firstRoomId, HOUSEHOLD_ID, secondRoomId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, '柜子', '柜子', 0, false, 0),
                       (?, ?, ?, '柜子', '柜子', 0, false, 0)
                """, firstCabinetId, HOUSEHOLD_ID, firstRoomId,
                secondCabinetId, HOUSEHOLD_ID, secondRoomId);
        chatModel.reset(
                "家庭事实显示柜子中没有咖啡机库存。",
                "知识来源说明咖啡机应定期清洁滤网。");

        String question = "这个物品在柜子里的库存和说明书维护要求是什么？";
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "answerScope": "BOTH",
                                  "pageContext": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(question, ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LOCATION"))));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "answerScope": "BOTH",
                                  "scope": {"type": "LOCATION", "id": "%s"},
                                  "confirmedScopes": [{"type": "ITEM", "id": "%s"}]
                                }
                                """.formatted(question, firstCabinetId, ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.targetScope.type").value("LOCATION"))
                .andExpect(jsonPath("$.targetScope.id").value(firstCabinetId.toString()))
                .andExpect(jsonPath("$.answerParts.length()").value(2));
    }

    @Test
    void itemScopeUsesCurrentHouseholdAndAllowedMountsOnly() throws Exception {
        insertAvailableAttachment(HOUSEHOLD_FILE_ID, HOUSEHOLD_SOURCE_ID,
                "家庭维护约定.txt", "HOUSEHOLD", HOUSEHOLD_ID);
        insertAvailableAttachment(LOT_FILE_ID, LOT_SOURCE_ID,
                "本批次维修记录.txt", "LOT", LOT_ID);
        vectorStore.add(List.of(
                document("家庭附件：每月清洁一次。", HOUSEHOLD_ID, "HOUSEHOLD", HOUSEHOLD_ID,
                        null, null, HOUSEHOLD_FILE_ID, 1, "维护约定", 0, 12),
                document("批次附件：只适用于 LOT-COFFEE-01。", HOUSEHOLD_ID, "LOT", LOT_ID,
                        ITEM_ID, LOT_ID, LOT_FILE_ID, 2, "维修记录", 0, 20),
                document("其他家庭的秘密清洁方法。", UUID.randomUUID(), "ITEM", ITEM_ID,
                        ITEM_ID, null, FILE_ID, 99, "其他家庭", 0, 14)));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机要怎么维护？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[*].attachmentId",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                FILE_ID.toString(), HOUSEHOLD_FILE_ID.toString())));

        assertThat(chatModel.lastPrompt())
                .contains("清洁时先取下滤网", "家庭附件：每月清洁一次")
                .doesNotContain("批次附件", "其他家庭的秘密");
    }

    @Test
    void recycledAttachmentIsExcludedFromGroundedAnswersWhileContentRemainsReadable() throws Exception {
        byte[] originalContent = "回收站内仍可读取的说明正文".getBytes(StandardCharsets.UTF_8);
        var readableAttachment = fileApi.store(
                HOUSEHOLD_ID,
                originalContent,
                "可下载说明.txt",
                "text/plain",
                FileApi.MOUNT_ITEM,
                ITEM_ID);
        insertAvailableKnowledgeSource(
                readableAttachment.id(), UUID.randomUUID(), FileApi.MOUNT_ITEM, ITEM_ID);
        vectorStore.add(List.of(document(
                "回收站内仍可读取的说明正文",
                HOUSEHOLD_ID, "ITEM", ITEM_ID, ITEM_ID, null, readableAttachment.id(),
                1, "回收测试", 0, originalContent.length)));

        fileApi.recycle(HOUSEHOLD_ID, FILE_ID);
        fileApi.recycle(HOUSEHOLD_ID, readableAttachment.id());
        assertThat(fileApi.readContent(HOUSEHOLD_ID, readableAttachment.id()))
                .hasValueSatisfying(content -> assertThat(content).isEqualTo(originalContent));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机滤网怎么清洁？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("NO_AVAILABLE_KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.jumps[0].type").value("ATTACHMENT"));

        assertThat(chatModel.callCount()).isZero();
    }

    @Test
    void remountedAttachmentUsesTheNewKnowledgeScopeImmediately() throws Exception {
        fileApi.remount(HOUSEHOLD_ID, FILE_ID, FileApi.MOUNT_LOT, LOT_ID);
        chatModel.reset("按批次说明执行清洁。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个批次怎么清洁？",
                                  "scope": {"type": "LOT", "id": "%s"}
                                }
                                """.formatted(LOT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.sources[0].attachmentId").value(FILE_ID.toString()))
                .andExpect(jsonPath("$.sources[0].mountType").value("LOT"))
                .andExpect(jsonPath("$.sources[0].mountId").value(LOT_ID.toString()));
    }

    @Test
    void renamedAttachmentAppearsWithItsNewNameInAnswerEvidence() throws Exception {
        fileApi.rename(HOUSEHOLD_ID, FILE_ID, "新版咖啡机说明.pdf");
        chatModel.reset("先取下滤网，用温水冲洗，完全晾干后再装回。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机滤网怎么清洁？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].attachmentName").value("新版咖啡机说明.pdf"));
    }

    @Test
    void lotScopeIncludesHouseholdItemAndCurrentLotEvidence() throws Exception {
        insertAvailableAttachment(HOUSEHOLD_FILE_ID, HOUSEHOLD_SOURCE_ID,
                "家庭维护约定.txt", "HOUSEHOLD", HOUSEHOLD_ID);
        insertAvailableAttachment(LOT_FILE_ID, LOT_SOURCE_ID,
                "本批次维修记录.txt", "LOT", LOT_ID);
        vectorStore.add(List.of(
                document("家庭附件：每月清洁一次。", HOUSEHOLD_ID, "HOUSEHOLD", HOUSEHOLD_ID,
                        null, null, HOUSEHOLD_FILE_ID, 1, "维护约定", 0, 12),
                document("批次附件：本机异响时检查研磨仓。", HOUSEHOLD_ID, "LOT", LOT_ID,
                        ITEM_ID, LOT_ID, LOT_FILE_ID, 2, "故障处理", 0, 18)));

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个批次的咖啡机异响怎么办？",
                                  "scope": {"type": "LOT", "id": "%s"}
                                }
                                """.formatted(LOT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(3))
                .andExpect(jsonPath("$.sources[*].attachmentId",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                FILE_ID.toString(), HOUSEHOLD_FILE_ID.toString(), LOT_FILE_ID.toString())))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItem("LOT")))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItem("ATTACHMENT")));
    }

    @Test
    void remountDuringPreparationCannotPublishChunksUnderTheOldScope() throws Exception {
        UUID fileId = fileApi.store(
                HOUSEHOLD_ID,
                "并发改挂后的批次专用清洁步骤。".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "并发改挂说明.txt",
                "text/plain",
                FileApi.MOUNT_ITEM,
                ITEM_ID).id();
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        embeddingModel.blockNextEmbedding();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var firstPreparation = executor.submit(
                    () -> preparationService.prepareDue(OffsetDateTime.now()));
            assertThat(embeddingModel.awaitBlocked()).isTrue();
            try {
                fileApi.remount(HOUSEHOLD_ID, fileId, FileApi.MOUNT_LOT, LOT_ID);
            } finally {
                embeddingModel.releaseEmbedding();
            }
            assertThat(firstPreparation.get(30, TimeUnit.SECONDS)).isOne();
        }

        mvc.perform(get("/api/v1/ai/knowledge-sources").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.fileId == '%s')].status".formatted(fileId))
                        .value("PROCESSING"));

        assertThat(preparationService.prepareDue(OffsetDateTime.now().plusSeconds(1))).isOne();
        chatModel.reset("按批次说明执行清洁。");
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个批次怎么清洁？",
                                  "scope": {"type": "LOT", "id": "%s"}
                                }
                                """.formatted(LOT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.sources[*].attachmentId")
                        .value(org.hamcrest.Matchers.hasItem(fileId.toString())))
                .andExpect(jsonPath("$.sources[?(@.attachmentId == '%s')].mountType".formatted(fileId))
                        .value("LOT"));
    }

    @Test
    void failedPreparationReturnsExplicitFailureWithAttachmentEntryWithoutCallingModel() throws Exception {
        jdbc.update("""
                UPDATE ai_knowledge_source
                SET status = 'FAILED', failure_code = 'TEXT_NOT_EXTRACTABLE',
                    failure_message = '扫描版 PDF 无法提取文字'
                WHERE id = ?
                """, SOURCE_ID);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机怎么清洁？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(true))
                .andExpect(jsonPath("$.reasonCode").value("KNOWLEDGE_SOURCE_PREPARATION_FAILED"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("解析失败")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("扫描版 PDF 无法提取文字")))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.jumps[0].type").value("ATTACHMENT"))
                .andExpect(jsonPath("$.jumps[0].attachmentId").value(FILE_ID.toString()));

        assertThat(chatModel.callCount()).isZero();
    }

    @Test
    void modelFailureReturnsAttachmentEntryWithoutUngroundedAnswer() throws Exception {
        chatModel.fail();

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "咖啡机怎么清洁？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(false))
                .andExpect(jsonPath("$.reasonCode").value("KNOWLEDGE_MODEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("无法依据资料生成回答")))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.jumps[0].type").value("ATTACHMENT"))
                .andExpect(jsonPath("$.jumps[0].attachmentId").value(FILE_ID.toString()));
    }

    @Test
    void itemOutsideCurrentHouseholdIsRejectedAtTheHttpBoundary() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "不属于当前家庭的物品怎么维护？",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("AI_INVALID_CONFIGURATION"));

        assertThat(chatModel.callCount()).isZero();
    }

    private void insertAvailableItemAttachment(UUID fileId, UUID sourceId, String name, UUID itemId) {
        insertAvailableAttachment(fileId, sourceId, name, "ITEM", itemId);
    }

    private void insertAvailableAttachment(
            UUID fileId,
            UUID sourceId,
            String name,
            String mountType,
            UUID mountId
    ) {
        jdbc.update("""
                INSERT INTO stored_file
                    (id, household_id, storage_key, original_filename, declared_media_type,
                     detected_media_type, byte_size, sha256, mount_type, mount_id,
                     name_normalized, created_at)
                VALUES (?, ?, ?, ?, 'text/plain', 'text/plain', 10, ?,
                        ?, ?, ?, CURRENT_TIMESTAMP)
                """, fileId, HOUSEHOLD_ID, "knowledge/" + fileId, name, "a".repeat(64), mountType, mountId,
                name.toLowerCase());
        insertAvailableKnowledgeSource(fileId, sourceId, mountType, mountId);
    }

    private void insertAvailableKnowledgeSource(
            UUID fileId,
            UUID sourceId,
            String mountType,
            UUID mountId
    ) {
        jdbc.update("""
                INSERT INTO ai_knowledge_source
                    (id, household_id, file_id, mount_type, mount_id, status,
                     selected_at, processed_at, processing_version)
                VALUES (?, ?, ?, ?, ?, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, sourceId, HOUSEHOLD_ID, fileId, mountType, mountId);
    }

    private static Document document(
            String text,
            UUID householdId,
            String mountType,
            UUID mountId,
            UUID itemId,
            UUID lotId,
            UUID attachmentId,
            Integer pageNumber,
            String sectionPath,
            int charStart,
            int charEnd
    ) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("household_id", householdId.toString());
        metadata.put("mount_type", mountType);
        metadata.put("mount_id", mountId.toString());
        if (itemId != null) metadata.put("item_id", itemId.toString());
        if (lotId != null) metadata.put("lot_id", lotId.toString());
        metadata.put("attachment_id", attachmentId.toString());
        metadata.put("readiness_status", "AVAILABLE");
        metadata.put("page_number", pageNumber);
        metadata.put("section_path", sectionPath);
        metadata.put("char_start", charStart);
        metadata.put("char_end", charEnd);
        metadata.put("embedding_model", "test-embedding");
        metadata.put("embedding_dimensions", 1024);
        metadata.put("chunker_version", "1");
        return new Document(UUID.randomUUID().toString(), text, metadata);
    }

    private RequestPostProcessor auth() {
        var principal = new ZijaPrincipal(OWNER_ACCOUNT_ID, "owner", "户主", "hash", true);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiSeam {

        @Bean
        @Primary
        CapturingChatModel capturingChatModel() {
            return new CapturingChatModel();
        }

        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }

        @Bean
        AiModelProvider deterministicAiModelProvider() {
            return new AiModelProvider() {
                @Override
                public String id() {
                    return "deterministic";
                }

                @Override
                public boolean requiresOutboundAccess() {
                    return false;
                }

                @Override
                public boolean requiresCredential() {
                    return false;
                }

                @Override
                public ProbeResult probe(AiProviderConfiguration configuration) {
                    return ProbeResult.available("fake-chat", "fake-embedding");
                }

                @Override
                public AiApi.ChatReply complete(AiApi.ChatRequest request, AiProviderConfiguration configuration) {
                    throw new UnsupportedOperationException("Q&A uses ChatClient");
                }

                @Override
                public AiApi.EmbeddingReply embed(
                        AiApi.EmbeddingRequest request,
                        AiProviderConfiguration configuration
                ) {
                    throw new UnsupportedOperationException("VectorStore uses EmbeddingModel");
                }
            };
        }
    }

    static final class CapturingChatModel implements ChatModel {

        private String factAnswer = "";
        private String knowledgeAnswer = "";
        private String lastPrompt = "";
        private int callCount;
        private boolean failing;

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount++;
            if (failing) {
                throw new IllegalStateException("model unavailable");
            }
            lastPrompt = prompt.getContents();
            boolean factPrompt = prompt.getOptions() instanceof ToolCallingChatOptions options
                    && options.getToolCallbacks() != null
                    && !options.getToolCallbacks().isEmpty();
            String answer = factPrompt ? factAnswer : knowledgeAnswer;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }

        @Override
        public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        void reset(String answer) {
            reset(answer, answer);
        }

        void reset(String factAnswer, String knowledgeAnswer) {
            this.factAnswer = factAnswer;
            this.knowledgeAnswer = knowledgeAnswer;
            this.lastPrompt = "";
            this.callCount = 0;
            this.failing = false;
        }

        String lastPrompt() {
            return lastPrompt;
        }

        int callCount() {
            return callCount;
        }

        void fail() {
            failing = true;
        }
    }

    static final class DeterministicEmbeddingModel implements EmbeddingModel {

        private final AtomicBoolean blockNext = new AtomicBoolean();
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            awaitReleaseIfRequested();
            return new EmbeddingResponse(request.getInstructions().stream()
                    .map(ignored -> new Embedding(vector(), 0, EmbeddingResultMetadata.EMPTY))
                    .toList());
        }

        @Override
        public float[] embed(Document document) {
            awaitReleaseIfRequested();
            return vector();
        }

        @Override
        public int dimensions() {
            return 1024;
        }

        void blockNextEmbedding() {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
            blockNext.set(true);
        }

        boolean awaitBlocked() throws InterruptedException {
            return blocked.await(10, TimeUnit.SECONDS);
        }

        void releaseEmbedding() {
            release.countDown();
        }

        void reset() {
            releaseEmbedding();
            blockNext.set(false);
            blocked = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        private void awaitReleaseIfRequested() {
            if (!blockNext.compareAndSet(true, false)) {
                return;
            }
            blocked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release embedding");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("embedding interrupted", exception);
            }
        }

        private static float[] vector() {
            float[] vector = new float[1024];
            vector[0] = 1.0f;
            return vector;
        }
    }
}

package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
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
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 家庭事实问答 HTTP 集成测试。
 *
 * <p>用确定性假 {@code ChatModel} 驱动真实 Spring AI 工具调用：假模型先发出一次工具调用，
 * Spring AI 执行真实 {@link HouseholdFactTools}（只读查询契约访问测试库），再把工具结果回喂
 * 给假模型产出最终摘要——证明「家族事实查询 + 最小假模型调用」全链路可运行，且结构化结果与
 * 跳转来自服务端工具而非模型叙述。</p>
 */
@AutoConfigureMockMvc
@Import(HouseholdFactQaEndpointIntegrationTest.FakeModelSeam.class)
class HouseholdFactQaEndpointIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID UNIT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID LOT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID KITCHEN_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID STOCK_POSITION_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID MOVEMENT_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ITEM_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_LOT_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_LOT_ID = UUID.fromString("50000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScriptedChatModel chatModel;

    @Autowired
    private AiApi aiApi;

    @Autowired
    private AiRequestGuard requestGuard;

    @MockitoBean
    private ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        requestGuard.reset();
        jdbc.update("""
                INSERT INTO ai_provider_setting(singleton_key, enabled, provider_id)
                VALUES (1, TRUE, 'deterministic')
                """);
        seedHouseholdMember();
        seedCatalog();
        seedInventory();
        chatModel.reset();
    }

    // ==================== 成功 ====================

    @Test
    void memberAsksAboutHouseholdStockAndGetsStructuredAnswerWithSourcesAndJumps() throws Exception {
        assertThat(aiApi.status().available()).isTrue();
        // 假模型：第一次调用发出 search_items 工具调用；第二次调用读取工具结果后产出摘要。
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> response.contains("UNAVAILABLE")
                        ? "暂时无法确认：家庭事实来源暂不可用。"
                        : "牛奶当前库存 5 瓶，放在厨房。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶还有多少？放在哪里？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("牛奶还有多少？放在哪里？"))
                .andExpect(jsonPath("$.modelAvailable").value(true))
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.dataTime").isNotEmpty())
                .andExpect(jsonPath("$.structuredResults[0].kind").value("ITEM_SEARCH"))
                .andExpect(jsonPath("$.structuredResults[0].title").value("物品搜索结果"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].当前总库存").value("5"))
                .andExpect(jsonPath("$.sources[0].category").value("HOUSEHOLD_FACT"))
                .andExpect(jsonPath("$.sources[0].dataTime").isNotEmpty())
                .andExpect(jsonPath("$.jumps[0].type").value("ITEM"))
                .andExpect(jsonPath("$.jumps[0].itemId").value(ITEM_ID.toString()));

        assertThat(chatModel.toolDispatchCount()).isEqualTo(1);
        assertThat(chatModel.modelCallCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void structuredResultsComeFromServerToolsNotFromModelNarration() throws Exception {
        chatModel.script(
                "itemStock", "{\"itemId\":\"%s\",\"limit\":10}".formatted(ITEM_ID),
                response -> "（模型任意）");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶放在哪里？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structuredResults[0].kind").value("ITEM_STOCK"))
                .andExpect(jsonPath("$.structuredResults[0].title").value("「牛奶」库存分布"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].位置").value("厨房"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].数量").value("5"))
                .andExpect(jsonPath("$.structuredResults[1].kind").value("ITEM_STOCK_TOTAL"))
                .andExpect(jsonPath("$.structuredResults[1].rows[0].当前总库存").value("5"))
                .andExpect(jsonPath("$.jumps[*].type").isNotEmpty());
    }

    @Test
    void autoScopeUsesQuestionAndAuthorizedPageContextWithoutRequiringManualSelection() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个物品怎么清洁？",
                                  "answerScope": "AUTO",
                                  "pageContext": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.usedAnswerScope").value("KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.targetScope.type").value("ITEM"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.targetScope.label").value("牛奶"))
                .andExpect(jsonPath("$.scopeReason").value(org.hamcrest.Matchers.containsString("当前页面")));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void neutralQuestionOnAnItemPageRecommendsBothSourcesFromThePageContext() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个呢？",
                                  "answerScope": "AUTO",
                                  "pageContext": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("BOTH"))
                .andExpect(jsonPath("$.usedAnswerScope").value("BOTH"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()));
    }

    @Test
    void knowledgeQuestionWithAnItemAndLocationUsesTheItemWithoutFalseAmbiguity() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶在厨房怎么保存？",
                                  "answerScope": "AUTO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.targetScope.type").value("ITEM"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.candidates").isEmpty());

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void factQuestionWithAnItemAndLocationKeepsTheirIntersection() throws Exception {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '咖啡机', 'DURABLE', ?, 'ACTIVE', 1)
                """, SECOND_ITEM_ID, HOUSEHOLD_ID, UNIT_ID);
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, lot_number, version)
                VALUES (?, ?, ?, 'COFFEE-001', 1)
                """, SECOND_LOT_ID, HOUSEHOLD_ID, SECOND_ITEM_ID);
        jdbc.update("""
                INSERT INTO inventory_stock_position(id, household_id, lot_id, location_id, quantity, revision)
                VALUES (?, ?, ?, ?, '2', 0)
                """, UUID.randomUUID(), HOUSEHOLD_ID, SECOND_LOT_ID, KITCHEN_ID);
        chatModel.script(
                "locationStock", "{\"itemKeyword\":\"牛奶\",\"limit\":10}",
                response -> "厨房内的牛奶库存见表格。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶在厨房还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetScope.type").value("LOCATION"))
                .andExpect(jsonPath("$.targetScope.id").value(KITCHEN_ID.toString()))
                .andExpect(jsonPath("$.structuredResults[0].kind").value("LOCATION_STOCK"))
                .andExpect(jsonPath("$.structuredResults[0].rows.length()").value(1))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].物品").value("牛奶"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].数量").value("5"));
    }

    @Test
    void explicitAnswerScopeOverridesRecommendationAndReportsTheActualRange() throws Exception {
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> "家庭事实中记录了牛奶库存。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶怎么清洁？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("KNOWLEDGE_SOURCE"))
                .andExpect(jsonPath("$.usedAnswerScope").value("HOUSEHOLD_FACT"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.sources[0].category").value("HOUSEHOLD_FACT"));
    }

    @Test
    void ambiguousItemNameReturnsCandidatesWithoutExecutingTheQueryUntilConfirmed() throws Exception {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '牛奶', 'CONSUMABLE', ?, 'ACTIVE', 1)
                """, SECOND_ITEM_ID, HOUSEHOLD_ID, UNIT_ID);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ITEM"))))
                .andExpect(jsonPath("$.candidates[*].detail",
                        org.hamcrest.Matchers.hasItems(
                                org.hamcrest.Matchers.containsString("编号 00000001"),
                                org.hamcrest.Matchers.containsString("编号 00000002"))))
                .andExpect(jsonPath("$.structuredResults").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());

        assertThat(chatModel.modelCallCount()).isZero();

        chatModel.script(
                "itemStock", "{\"itemId\":\"%s\",\"limit\":10}".formatted(ITEM_ID),
                response -> "已按确认的物品查询。 ");
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.targetScope.id").value(ITEM_ID.toString()));

        assertThat(chatModel.modelCallCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void explicitFactScopeOutsideTheCurrentHouseholdIsRejectedBeforeModelAccess() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .header("X-Request-Id", "qa-invalid-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个物品还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("AI_INVALID_CONFIGURATION"));

        assertThat(chatModel.modelCallCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE action = 'AI_HOUSEHOLD_QA'
                  AND outcome = 'FAILURE'
                  AND request_id = 'qa-invalid-scope'
                  AND detail ->> 'reasonCode' = 'AI_QA_INVALID_REQUEST'
                """, Integer.class)).isOne();
    }

    @Test
    void invalidScopeTypeIsAuditedBeforeReturningTheValidationFailure() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .header("X-Request-Id", "qa-invalid-scope-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个范围里有什么？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "UNKNOWN", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("AI_INVALID_CONFIGURATION"));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE action = 'AI_HOUSEHOLD_QA'
                  AND outcome = 'FAILURE'
                  AND request_id = 'qa-invalid-scope-type'
                  AND detail ->> 'reasonCode' = 'AI_QA_INVALID_REQUEST'
                """, Integer.class)).isOne();
    }

    @Test
    void duplicatedLotSerialReturnsLotCandidatesBeforeQueryExecution() throws Exception {
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, lot_number, serial_number, version)
                VALUES (?, ?, ?, 'LOT-002', 'SN-SAME', 1),
                       (?, ?, ?, 'LOT-003', 'SN-SAME', 1)
                """, SECOND_LOT_ID, HOUSEHOLD_ID, ITEM_ID,
                THIRD_LOT_ID, HOUSEHOLD_ID, ITEM_ID);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "序列号 SN-SAME 的批次放在哪里？",
                                  "answerScope": "HOUSEHOLD_FACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LOT"))));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void duplicatedLocationLeafNameReturnsPathQualifiedCandidatesBeforeQueryExecution() throws Exception {
        UUID pantryId = UUID.randomUUID();
        UUID garageId = UUID.randomUUID();
        UUID pantryDrawerId = UUID.randomUUID();
        UUID garageDrawerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '储藏室', '储藏室', 0, false, 0),
                       (?, ?, NULL, '车库', '车库', 0, false, 0)
                """, pantryId, HOUSEHOLD_ID, garageId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO location
                    (id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, '抽屉', '抽屉', 0, false, 0),
                       (?, ?, ?, '抽屉', '抽屉', 0, false, 0)
                """, pantryDrawerId, HOUSEHOLD_ID, pantryId,
                garageDrawerId, HOUSEHOLD_ID, garageId);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "抽屉里有什么？",
                                  "answerScope": "HOUSEHOLD_FACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("AMBIGUOUS_TARGET"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[*].type",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LOCATION"))))
                .andExpect(jsonPath("$.candidates[*].detail",
                        org.hamcrest.Matchers.hasItems(
                                org.hamcrest.Matchers.containsString("储藏室 / 抽屉"),
                                org.hamcrest.Matchers.containsString("车库 / 抽屉"))));

        assertThat(chatModel.modelCallCount()).isZero();

        jdbc.update("UPDATE inventory_stock_position SET location_id = ? WHERE id = ?",
                pantryDrawerId, STOCK_POSITION_ID);
        chatModel.script(
                "locationStock", "{\"itemKeyword\":\"\",\"limit\":10}",
                response -> response.contains("储藏室 / 抽屉") && response.contains("牛奶")
                        ? "已按确认的位置查询。" : "暂时无法确认。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "抽屉里有什么？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "LOCATION", "id": "%s"}
                                }
                                """.formatted(pantryDrawerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("已按确认的位置查询。"))
                .andExpect(jsonPath("$.structuredResults[0].kind").value("LOCATION_STOCK"))
                .andExpect(jsonPath("$.structuredResults[0].title").value(
                        org.hamcrest.Matchers.containsString("储藏室 / 抽屉")))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].物品").value("牛奶"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].数量").value("5"))
                .andExpect(jsonPath("$.jumps[*].type",
                        org.hamcrest.Matchers.hasItems("ITEM", "LOT", "LOCATION")));
    }

    @Test
    void confirmedLotScopeRejectsModelRequestsForAnotherItem() throws Exception {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '咖啡机', 'DURABLE', ?, 'ACTIVE', 1)
                """, SECOND_ITEM_ID, HOUSEHOLD_ID, UNIT_ID);
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, lot_number, version)
                VALUES (?, ?, ?, 'COFFEE-001', 1)
                """, SECOND_LOT_ID, HOUSEHOLD_ID, SECOND_ITEM_ID);

        chatModel.script(
                "itemStock", "{\"itemId\":\"%s\",\"limit\":10}".formatted(SECOND_ITEM_ID),
                response -> response.contains("UNAVAILABLE")
                        ? "暂时无法确认：已确认的批次范围不支持查询其他物品。"
                        : "错误地返回了其他物品库存。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个批次放在哪里？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "LOT", "id": "%s"}
                                }
                                """.formatted(LOT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(
                        org.hamcrest.Matchers.containsString("暂时无法确认")))
                .andExpect(jsonPath("$.structuredResults").isEmpty());

        assertThat(chatModel.firstPrompt()).contains(
                "LOT", LOT_ID.toString(), "LOT-001", ITEM_ID.toString());
    }

    // ==================== 权限失败 ====================

    @Test
    void nonMemberCannotAskHouseholdFacts() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new ZijaPrincipal(
                                        UUID.randomUUID(), "stranger", "外来者", "hash", true),
                                "hash")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶还有多少？\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAskHouseholdFacts() throws Exception {
        mvc.perform(post("/api/v1/ai/qa")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶还有多少？\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 模型不可用 ====================

    @Test
    void modelUnavailableReturnsControlledStructuredFactsWithoutModelCall() throws Exception {
        jdbc.update("UPDATE ai_provider_setting SET enabled = FALSE WHERE singleton_key = 1");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶还有多少、放在哪里？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(false))
                .andExpect(jsonPath("$.reasonCode").value("STRUCTURED_FACTS_FALLBACK"))
                .andExpect(jsonPath("$.summary").value(
                        org.hamcrest.Matchers.containsString("AI 模型当前不可用")))
                .andExpect(jsonPath("$.structuredResults[*].kind",
                        org.hamcrest.Matchers.hasItems("ITEM_STOCK", "ITEM_STOCK_TOTAL")))
                .andExpect(jsonPath("$.structuredResults[?(@.kind == 'ITEM_STOCK_TOTAL')].rows[0].当前总库存")
                        .value("5"))
                .andExpect(jsonPath("$.sources[0].category").value("HOUSEHOLD_FACT"))
                .andExpect(jsonPath("$.sources[0].available").value(true))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItems("ITEM", "LOT", "LOCATION")));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void configuredProviderControlsWhichQaClientCanReceiveHouseholdData() throws Exception {
        jdbc.update("""
                UPDATE ai_provider_setting
                SET provider_id = 'selected-without-qa-client'
                WHERE singleton_key = 1
                """);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("STRUCTURED_FACTS_FALLBACK"));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    // ==================== 事实来源失败（工具捕获异常 → 不补答） ====================

    @Test
    void factSourceFailureAnswersCannotConfirmWithoutInventing() throws Exception {
        // seed 一个在仓库中不存在的物品 —— itemStock 查询契约会抛错，工具应返回 unavailable
        String unknownItem = UUID.randomUUID().toString();
        chatModel.script(
                "itemStock", "{\"itemId\":\"%s\",\"limit\":10}".formatted(unknownItem),
                response -> response.contains("UNAVAILABLE")
                        ? "暂时无法确认：该物品的库存信息暂不可用。"
                        : "找到了库存！");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"某件不在的物品库存如何？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(true))
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("暂时无法确认")));
    }

    @Test
    void movementsUseImmutableMovementAsFactBasis() throws Exception {
        chatModel.script(
                "itemMovements", "{\"itemId\":\"%s\",\"limit\":10}".formatted(ITEM_ID),
                response -> "最近有一笔入库流水。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶最近为什么数量变化？谁操作的？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structuredResults[0].kind").value("MOVEMENTS"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].类型").value("INBOUND"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].原因").value("购入"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].操作人").value("户主"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].时间").isNotEmpty())
                .andExpect(jsonPath("$.structuredResults[0].rows[0].到").value("厨房"))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItem("MOVEMENT")));
    }

    @Test
    void expiringLotsToolReturnsBoundStructuredFacts() throws Exception {
        chatModel.script(
                "expiringLots", "{\"withinDays\":30,\"limit\":10}",
                response -> "有临期批次。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"哪些批次快到期了？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structuredResults[0].kind").value("EXPIRING_LOTS"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].物品").value("牛奶"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].剩余天数").isNotEmpty())
                .andExpect(jsonPath("$.structuredResults[0].rows[0].数量").value("5"))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItem("REMINDER")));
    }

    @Test
    void lowStockToolReturnsStructuredFactsAndReminderJump() throws Exception {
        chatModel.script(
                "lowStock", "{\"limit\":10}",
                response -> "有低库存物品。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"有哪些物品缺货了？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structuredResults[0].kind").value("LOW_STOCK"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].物品").value("牛奶"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].当前库存").value("5"))
                .andExpect(jsonPath("$.structuredResults[0].rows[0].阈值").value("10"))
                .andExpect(jsonPath("$.jumps[*].type", org.hamcrest.Matchers.hasItem("REMINDER")));
    }

    /** 最小假模型调用（只要一次工具调用即可完成回答） */

    @Test
    void minimalFakeModelInvocationCompletesAnswerWithOneToolCall() throws Exception {
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> "牛奶有库存，请查看表格。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"有没有牛奶？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(true))
                .andExpect(jsonPath("$.summary").value("牛奶有库存，请查看表格。"));

        assertThat(chatModel.toolDispatchCount()).isEqualTo(1);
    }

    @Test
    void qaAuditStoresOnlyRequestMetadataAndGroundingCount() throws Exception {
        String requestId = "qa-audit-request-44";
        String sensitiveQuestion = "牛奶还有多少？不要把这个问题写入审计";
        String sensitiveAnswer = "牛奶有库存，这段回答也不能进入审计。";
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> sensitiveAnswer);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + sensitiveQuestion + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"));

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT actor_account_id, request_id, outcome, detail::text AS detail
                FROM audit_log
                WHERE action = 'AI_HOUSEHOLD_QA'
                """);
        assertThat(audit.get("actor_account_id")).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(audit.get("request_id")).isEqualTo(requestId);
        assertThat(audit.get("outcome")).isEqualTo("SUCCESS");
        assertThat(String.valueOf(audit.get("detail")))
                .contains("\"providerId\": \"deterministic\"")
                .contains("\"groundingCount\": \"1\"")
                .doesNotContain(sensitiveQuestion, sensitiveAnswer, "question", "answer");
    }

    @Test
    void qaAuditKeepsTheProviderSelectedAtRequestStart() throws Exception {
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> {
                    jdbc.update("""
                            UPDATE ai_provider_setting
                            SET provider_id = 'selected-without-qa-client'
                            WHERE singleton_key = 1
                            """);
                    return "牛奶有库存。";
                });

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .header("X-Request-Id", "qa-provider-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶还有多少？\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT detail ->> 'providerId'
                FROM audit_log
                WHERE action = 'AI_HOUSEHOLD_QA'
                  AND request_id = 'qa-provider-snapshot'
                """, String.class)).isEqualTo("deterministic");
    }

    @Test
    void memberRateLimitReturnsStableProblemReason() throws Exception {
        jdbc.update("""
                UPDATE ai_provider_setting
                SET requests_per_minute = 10, member_requests_per_minute = 1
                WHERE singleton_key = 1
                """);
        chatModel.script(
                "searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}",
                response -> "牛奶有库存。 ");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .header("X-Request-Id", "qa-rate-first")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶还有多少？\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .header("X-Request-Id", "qa-rate-limited")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"牛奶放在哪里？\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI_REQUEST_LIMITED"))
                .andExpect(jsonPath("$.reasonCode").value("AI_MEMBER_RATE_LIMITED"))
                .andExpect(jsonPath("$.requestId").exists());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE action = 'AI_HOUSEHOLD_QA'
                  AND outcome = 'FAILURE'
                  AND request_id = 'qa-rate-limited'
                  AND detail ->> 'reasonCode' = 'AI_MEMBER_RATE_LIMITED'
                  AND detail ->> 'groundingCount' = '0'
                """, Integer.class)).isOne();
    }

    @Test
    void oversizedQaContextReturnsStableProblemReasonBeforeModelCall() throws Exception {
        jdbc.update("""
                UPDATE ai_provider_setting
                SET max_context_tokens = 256
                WHERE singleton_key = 1
                """);

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "问".repeat(1500) + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("AI_REQUEST_LIMITED"))
                .andExpect(jsonPath("$.reasonCode").value("AI_CONTEXT_LIMIT_EXCEEDED"));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void toolLoopStopsBeforeSecondModelCallWhenToolResultExceedsContextBudget() throws Exception {
        jdbc.update("""
                UPDATE ai_provider_setting
                SET max_context_tokens = 4096
                WHERE singleton_key = 1
                """);
        chatModel.script("largeResult", "{}", response -> "不应生成");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"请查询家庭事实\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("AI_REQUEST_LIMITED"))
                .andExpect(jsonPath("$.reasonCode").value("AI_CONTEXT_LIMIT_EXCEEDED"));

        assertThat(chatModel.modelCallCount()).isOne();
    }

    @Test
    void modelTimeoutFallsBackToControlledStructuredFacts() throws Exception {
        jdbc.update("""
                UPDATE ai_provider_setting
                SET request_timeout_seconds = 1
                WHERE singleton_key = 1
                """);
        chatModel.delayNextCall();

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "牛奶还有多少？",
                                  "answerScope": "HOUSEHOLD_FACT",
                                  "scope": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(false))
                .andExpect(jsonPath("$.reasonCode").value("STRUCTURED_FACTS_FALLBACK"))
                .andExpect(jsonPath("$.structuredResults[?(@.kind == 'ITEM_STOCK_TOTAL')].rows[0].当前总库存")
                        .value("5"));

        assertThat(chatModel.interruptedCallCount()).isOne();
    }

    // ==================== helpers ====================

    private RequestPostProcessor auth() {
        var principal = new ZijaPrincipal(OWNER_ACCOUNT_ID, "owner", "户主", "hash", true);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }

    private void seedHouseholdMember() {
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
    }

    private void seedCatalog() {
        jdbc.update("""
                INSERT INTO catalog_unit(id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, '瓶', '瓶', 0, 'ACTIVE')
                """, UNIT_ID, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, low_stock_mode,
                     low_stock_threshold, status, version)
                VALUES (?, ?, '牛奶', 'CONSUMABLE', ?, 'CUSTOM', '10', 'ACTIVE', 1)
                """, ITEM_ID, HOUSEHOLD_ID, UNIT_ID);
    }

    private void seedInventory() {
        jdbc.update("""
                INSERT INTO location(id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, '厨房', '厨房', 0, false, 0)
                """, KITCHEN_ID, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, expiry_date, lot_number, version)
                VALUES (?, ?, ?, ?, 'LOT-001', 1)
                """, LOT_ID, HOUSEHOLD_ID, ITEM_ID, LocalDate.now().plusDays(30));
        jdbc.update("""
                INSERT INTO inventory_stock_position(id, household_id, lot_id, location_id, quantity, revision)
                VALUES (?, ?, ?, ?, '5', 0)
                """, STOCK_POSITION_ID, HOUSEHOLD_ID, LOT_ID, KITCHEN_ID);
        jdbc.update("""
                INSERT INTO inventory_movement
                    (id, household_id, lot_id, item_id, type, quantity, from_location_id,
                     to_location_id, reason, operator_account_id, business_time,
                     created_at, idempotency_key)
                VALUES (?, ?, ?, ?, 'INBOUND', '5', NULL, ?, '购入', ?,
                        ?, ?, ?)
                """, MOVEMENT_ID, HOUSEHOLD_ID, LOT_ID, ITEM_ID, KITCHEN_ID,
                OWNER_ACCOUNT_ID, Timestamp.from(OffsetDateTime.now().toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant()),
                UUID.randomUUID().toString());
    }

    // ==================== Spring AI 假模型 seam ====================

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelSeam {

        @Bean
        @org.springframework.context.annotation.Primary
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }

        /** 让 {@code AiService.status()} 在该测试上下文返回可用，驱动真正的模型调用路径。 */
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
                    throw new UnsupportedOperationException("Q&A 走 Spring AI ChatClient，不经 provider seam");
                }

                @Override
                public AiApi.EmbeddingReply embed(AiApi.EmbeddingRequest request, AiProviderConfiguration configuration) {
                    throw new UnsupportedOperationException("不在本测试范围");
                }
            };
        }

        @Bean
        AiQaModelProvider deterministicQaModelProvider(
                org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder
        ) {
            return new AiQaModelProvider() {
                @Override
                public String id() {
                    return "deterministic";
                }

                @Override
                public String completeQa(
                        String systemPrompt,
                        String userPrompt,
                        Object[] tools,
                        AiProviderConfiguration configuration
                ) {
                    var prompt = chatClientBuilder.build().prompt()
                            .system(systemPrompt)
                            .user(userPrompt);
                    if (tools.length > 0) {
                        prompt.tools(tools)
                                .tools(new LargeTool())
                                .advisors(new AiContextBudgetAdvisor(configuration.maxContextTokens()));
                    }
                    return prompt.call().content();
                }
            };
        }

        @Bean
        AiModelProvider selectedProviderWithoutQaClient() {
            return new AiModelProvider() {
                @Override
                public String id() {
                    return "selected-without-qa-client";
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
                    return ProbeResult.available("selected-chat", "selected-embedding");
                }

                @Override
                public AiApi.ChatReply complete(
                        AiApi.ChatRequest request,
                        AiProviderConfiguration configuration
                ) {
                    throw new UnsupportedOperationException("provider has no Q&A client");
                }

                @Override
                public AiApi.EmbeddingReply embed(
                        AiApi.EmbeddingRequest request,
                        AiProviderConfiguration configuration
                ) {
                    throw new UnsupportedOperationException("provider has no embedding client");
                }
            };
        }
    }

    static final class LargeTool {

        @Tool(description = "返回用于上下文预算回归测试的超长家庭事实结果")
        String largeResult() {
            return "家庭事实".repeat(2_000);
        }
    }

    /**
     * 确定性假 {@link ChatModel}：第一次调用读取已注册工具并发出一次 {@code toolCall}，
     * 后续调用（收到 {@link ToolResponseMessage} 后）根据工具结果产出最终摘要。
     * 也是「最小假模型调用」的验证 seam。
     */
    static final class ScriptedChatModel implements ChatModel {

        private final AtomicInteger modelCalls = new AtomicInteger();
        private final AtomicInteger toolDispatchCount = new AtomicInteger();
        private final AtomicInteger interruptedCalls = new AtomicInteger();

        private String toolName = "searchItems";
        private String toolArguments = "{\"keyword\":\"牛奶\",\"limit\":10}";
        private ToolConsumer finalText = response -> "完成。";
        private String firstPrompt = "";
        private final java.util.concurrent.atomic.AtomicBoolean delayNextCall =
                new java.util.concurrent.atomic.AtomicBoolean();

        @Override
        public ChatResponse call(Prompt prompt) {
            if (delayNextCall.compareAndSet(true, false)) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    interruptedCalls.incrementAndGet();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("model call interrupted", exception);
                }
            }
            int call = modelCalls.incrementAndGet();
            if (call == 1) {
                firstPrompt = prompt.getContents();
                if (prompt.getOptions() instanceof ToolCallingChatOptions options
                        && options.getToolCallbacks() != null) {
                    boolean registered = options.getToolCallbacks().stream()
                            .anyMatch(callback -> callback.getToolDefinition().name().equals(toolName));
                    toolDispatchCount.addAndGet(registered ? 1 : 0);
                }
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder()
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "tool-call-" + call,
                                        "function",
                                        toolName,
                                        toolArguments)))
                                .build())));
            }
            ToolResponseMessage toolResponse = prompt.getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast)
                    .findFirst()
                    .orElse(null);
            String content = toolResponse != null
                    ? toolResponse.getResponses().stream()
                            .map(ToolResponseMessage.ToolResponse::responseData)
                            .reduce("", (a, b) -> a + b)
                    : "";
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(finalText.apply(content)))));
        }

        @Override
        public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        void script(String toolName, String toolArguments, ToolConsumer finalText) {
            this.toolName = toolName;
            this.toolArguments = toolArguments;
            this.finalText = finalText;
        }

        void reset() {
            modelCalls.set(0);
            toolDispatchCount.set(0);
            interruptedCalls.set(0);
            toolName = "searchItems";
            toolArguments = "{\"keyword\":\"牛奶\",\"limit\":10}";
            finalText = response -> "完成。";
            firstPrompt = "";
            delayNextCall.set(false);
        }

        void delayNextCall() {
            delayNextCall.set(true);
        }

        int modelCallCount() {
            return modelCalls.get();
        }

        int toolDispatchCount() {
            return toolDispatchCount.get();
        }

        int interruptedCallCount() {
            return interruptedCalls.get();
        }

        String firstPrompt() {
            return firstPrompt;
        }

        interface ToolConsumer {
            String apply(String toolResponse);
        }
    }
}

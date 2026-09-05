package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问答范围预览：只跑确定性词表，不得调用聊天/向量模型，也不得做全量物品批次扫描。
 */
@AutoConfigureMockMvc
@Import(HouseholdFactQaEndpointIntegrationTest.FakeModelSeam.class)
class HouseholdFactQaScopePreviewIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID UNIT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN_ITEM_ID = UUID.fromString("40000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HouseholdFactQaEndpointIntegrationTest.ScriptedChatModel chatModel;

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
                VALUES (?, ?, '瓶', '瓶', 0, 'ACTIVE')
                """, UNIT_ID, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, '牛奶', 'CONSUMABLE', ?, 'ACTIVE', 1)
                """, ITEM_ID, HOUSEHOLD_ID, UNIT_ID);
        chatModel.reset();
        chatModel.script("searchItems", "{\"keyword\":\"牛奶\",\"limit\":10}", response -> "不应生成");
    }

    @ParameterizedTest
    @CsvSource({
            "牛奶还有多少？, HOUSEHOLD_FACT",
            "滤网怎么清洁？, KNOWLEDGE_SOURCE",
            "过期了怎么处理, BOTH",
            "这个呢？, HOUSEHOLD_FACT"
    })
    void previewRecommendsFromSharedVocabWithoutCallingTheModel(String question, String scope)
            throws Exception {
        mvc.perform(post("/api/v1/ai/qa/scope-preview")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"%s\"}".formatted(question)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value(scope));

        assertThat(chatModel.modelCallCount()).isZero();
        assertThat(chatModel.firstPrompt()).isEmpty();
    }

    @Test
    void previewUsesAuthorizedItemPageContextForNoHitQuestions() throws Exception {
        mvc.perform(post("/api/v1/ai/qa/scope-preview")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个呢？",
                                  "pageContext": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("BOTH"));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    @Test
    void previewIgnoresUnauthorizedPageContextInsteadOfFailing() throws Exception {
        mvc.perform(post("/api/v1/ai/qa/scope-preview")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "这个呢？",
                                  "pageContext": {"type": "ITEM", "id": "%s"}
                                }
                                """.formatted(FOREIGN_ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAnswerScope").value("HOUSEHOLD_FACT"));

        assertThat(chatModel.modelCallCount()).isZero();
    }

    private RequestPostProcessor auth() {
        var principal = new ZijaPrincipal(OWNER_ACCOUNT_ID, "owner", "户主", "hash", true);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }
}

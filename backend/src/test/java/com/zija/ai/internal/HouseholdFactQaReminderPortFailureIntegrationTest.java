package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.reminder.ReminderApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 提醒只读端口失败时的家庭事实问答：{@code @MockitoBean ReminderApi}
 * 会替换本类全部测试的端口实现，因此与成功路径拆开。
 */
@AutoConfigureMockMvc
@Import(HouseholdFactQaEndpointIntegrationTest.FakeModelSeam.class)
class HouseholdFactQaReminderPortFailureIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

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

    @MockitoBean
    private ReminderApi reminderApi;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        requestGuard.reset();
        when(reminderApi.priorityTasks(any(), anyInt()))
                .thenThrow(new RuntimeException("reminder port down"));
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
        chatModel.reset();
    }

    @Test
    void openReminderTasksPortFailureAnswersCannotConfirm() throws Exception {
        chatModel.script(
                "openReminderTasks", "{\"limit\":10}",
                response -> response.contains("UNAVAILABLE")
                        ? "暂时无法确认：提醒任务来源暂不可用。"
                        : "编造了待处理提醒。");

        mvc.perform(post("/api/v1/ai/qa")
                        .with(auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"有哪些待处理提醒？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.summary").value(
                        org.hamcrest.Matchers.containsString("暂时无法确认")))
                .andExpect(jsonPath("$.sources[0].available").value(false))
                .andExpect(jsonPath("$.structuredResults").isEmpty());
    }

    private RequestPostProcessor auth() {
        var principal = new ZijaPrincipal(OWNER_ACCOUNT_ID, "owner", "户主", "hash", true);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }
}

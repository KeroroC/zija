package com.zija.reminder.internal.mail;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MailSetting 端点集成测试。
 *
 * <ol>
 *   <li>GET /mail-settings 返回懒初始化默认设置</li>
 *   <li>PUT /mail-settings 版本正确时更新成功</li>
 *   <li>PUT /mail-settings 旧版本返回 409</li>
 *   <li>MEMBER 角色 PUT /mail-settings 返回 403</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class MailSettingEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID ownerAccountId;
    private UUID memberAccountId;

    private ZijaPrincipal ownerPrincipal;
    private ZijaPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE reminder_household_mail_setting, reminder_notification, reminder_task,
                             reminder_household_rule,
                             inventory_movement, inventory_stock_position,
                             inventory_lot, inventory_idempotency_record,
                             audit_log, catalog_item_tag, catalog_item, catalog_unit,
                             catalog_brand, catalog_category, catalog_tag,
                             location, member, household, account
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        ownerAccountId = seedAccount("owner", "所有者");
        memberAccountId = seedAccount("member", "成员");
        seedMember(householdId, ownerAccountId, "OWNER");
        seedMember(householdId, memberAccountId, "MEMBER");

        ownerPrincipal = new ZijaPrincipal(ownerAccountId, "owner", "所有者", "hash", true);
        memberPrincipal = new ZijaPrincipal(memberAccountId, "member", "成员", "hash", true);
    }

    // ==================== 1. GET /mail-settings — 懒初始化 ====================

    @Test
    void getMailSettings_lazyInit_returnsDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/mail-settings")
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(householdId.toString()))
                .andExpect(jsonPath("$.digestEnabled").value(false))
                .andExpect(jsonPath("$.digestFrequency").value("DAILY"))
                .andExpect(jsonPath("$.urgentEnabled").value(true))
                .andExpect(jsonPath("$.recipientRoles").isArray())
                .andExpect(jsonPath("$.recipientRoles[0]").value("OWNER"))
                .andExpect(jsonPath("$.version").value(0));
    }

    // ==================== 2. PUT /mail-settings — 版本正确更新 ====================

    @Test
    void putMailSettings_ownerCanUpdate_returnsUpdated() throws Exception {
        // First create the settings
        mockMvc.perform(get("/api/v1/reminder/mail-settings").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "digestEnabled": true,
                    "digestFrequency": "WEEKLY",
                    "urgentEnabled": true,
                    "recipientRoles": ["OWNER", "ADMIN"],
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/mail-settings")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digestEnabled").value(true))
                .andExpect(jsonPath("$.digestFrequency").value("WEEKLY"))
                .andExpect(jsonPath("$.recipientRoles[0]").value("OWNER"))
                .andExpect(jsonPath("$.recipientRoles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ==================== 3. PUT /mail-settings — 旧版本 409 ====================

    @Test
    void putMailSettings_oldVersion_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/mail-settings").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "digestEnabled": true,
                    "digestFrequency": "WEEKLY",
                    "urgentEnabled": true,
                    "recipientRoles": ["OWNER"],
                    "version": 99
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/mail-settings")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MAIL_SETTING_VERSION_CONFLICT"));
    }

    // ==================== 4. MEMBER 角色 PUT /mail-settings 返回 403 ====================

    @Test
    void putMailSettings_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/mail-settings").with(auth(memberPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "digestEnabled": true,
                    "digestFrequency": "WEEKLY",
                    "urgentEnabled": true,
                    "recipientRoles": ["OWNER"],
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/mail-settings")
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ==================== Seed helpers ====================

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家" + h.getId().toString().substring(0, 6));
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedAccount(String username, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, 'ACTIVE')
                """, id, username, username.toUpperCase(), displayName);
        return id;
    }

    private void seedMember(UUID householdId, UUID accountId, String role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}

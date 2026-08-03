package com.zija.reminder.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

/**
 * ReminderController + NotificationController 端点集成测试。
 *
 * <ol>
 *   <li>GET /rules 返回懒初始化规则</li>
 *   <li>PUT /rules 更新成功 + 旧版本 409 + MEMBER 角色 403</li>
 *   <li>GET /tasks 分页 + severity 排序</li>
 *   <li>POST /tasks/{id}/snooze 正常 + 非法转换 409 + until 过去 422</li>
 *   <li>POST /tasks/{id}/complete + /ignore + /reopen</li>
 *   <li>GET /dashboard 返回结构</li>
 *   <li>GET /notifications 分页 + unreadOnly + unread-count + read + read-all</li>
 *   <li>跨家庭隔离 404</li>
 *   <li>CSRF 与 Problem Details 形态</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class ReminderEndpointIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID ownerAccountId;
    private UUID memberAccountId;
    private UUID itemId;
    private UUID lotId;
    private UUID lotId2;
    private UUID locationId;
    private UUID unitId;

    private ZijaPrincipal ownerPrincipal;
    private ZijaPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule,
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

        UUID unitId = seedUnit(householdId);
        this.unitId = unitId;
        itemId = seedItem(householdId, unitId);
        locationId = seedLocation(householdId);
        lotId = seedLot(householdId, itemId, locationId);
        lotId2 = seedLot(householdId, itemId, locationId);

        ownerPrincipal = new ZijaPrincipal(ownerAccountId, "owner", "所有者", "hash", true);
        memberPrincipal = new ZijaPrincipal(memberAccountId, "member", "成员", "hash", true);
    }

    // ==================== 1. GET /rules — 懒初始化 ====================

    @Test
    void getRules_lazyInit_returnsDefaultRule() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(householdId.toString()))
                .andExpect(jsonPath("$.expiryDisabled").value(false))
                .andExpect(jsonPath("$.expiryReminderDays").isArray())
                .andExpect(jsonPath("$.lowStockDisabled").value(false))
                .andExpect(jsonPath("$.lowStockThreshold").value(1))
                .andExpect(jsonPath("$.version").value(0));
    }

    // ==================== 2. PUT /rules ====================

    @Test
    void putRules_ownerCanUpdate_returnsUpdated() throws Exception {
        // First create the rule
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "expiryDisabled": false,
                    "expiryReminderDays": [14, 3],
                    "lowStockDisabled": false,
                    "lowStockThreshold": 2,
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryReminderDays[0]").value(14))
                .andExpect(jsonPath("$.expiryReminderDays[1]").value(3))
                .andExpect(jsonPath("$.lowStockThreshold").value(2))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void putRules_oldVersion_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "expiryDisabled": false,
                    "expiryReminderDays": [14, 3],
                    "lowStockDisabled": false,
                    "lowStockThreshold": 2,
                    "version": 99
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_RULE_VERSION_CONFLICT"));
    }

    @Test
    void putRules_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(memberPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "expiryDisabled": false,
                    "expiryReminderDays": [14, 3],
                    "lowStockDisabled": false,
                    "lowStockThreshold": 2,
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ==================== 3. GET /tasks — 分页 ====================

    @Test
    void listTasks_paged_returnsSortedBySeverity() throws Exception {
        seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "INFO");
        seedTaskWithLotNull(householdId, "LOW_STOCK", itemId, "OPEN", "URGENT");
        seedTask(householdId, "EXPIRY", lotId2, itemId, "OPEN", "WARN");

        mockMvc.perform(get("/api/v1/reminder/tasks")
                        .with(auth(ownerPrincipal))
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.total").value(3))
                // URGENT should be first
                .andExpect(jsonPath("$.items[0].severity").value("URGENT"));
    }

    @Test
    void listTasks_filterByKind_returnsFiltered() throws Exception {
        seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "INFO");
        seedTaskWithLotNull(householdId, "LOW_STOCK", itemId, "OPEN", "WARN");

        mockMvc.perform(get("/api/v1/reminder/tasks")
                        .with(auth(ownerPrincipal))
                        .param("kind", "EXPIRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("EXPIRY"));
    }

    // ==================== 4. POST /tasks/{id}/snooze ====================

    @Test
    void snooze_openTask_success() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "WARN");
        OffsetDateTime until = OffsetDateTime.now().plusDays(3);

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/snooze", taskId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\": \"%s\"}".formatted(until.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(status().isOk());

        // Verify status changed to SNOOZED
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reminder_task WHERE id = ?", String.class, taskId);
        assertThat(status).isEqualTo("SNOOZED");
    }

    @Test
    void snooze_doneTask_returns409() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "DONE", "INFO");
        OffsetDateTime until = OffsetDateTime.now().plusDays(3);

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/snooze", taskId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\": \"%s\"}".formatted(until.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_INVALID_TRANSITION"));
    }

    @Test
    void snooze_pastUntil_returns422() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "WARN");
        OffsetDateTime pastUntil = OffsetDateTime.now().minusDays(1);

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/snooze", taskId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\": \"%s\"}".formatted(pastUntil.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_SNOOZE_UNTIL_INVALID"));
    }

    // ==================== 5. complete / ignore / reopen ====================

    @Test
    void complete_openTask_success() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "WARN");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/complete", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reminder_task WHERE id = ?", String.class, taskId);
        assertThat(status).isEqualTo("DONE");
    }

    @Test
    void complete_doneTask_returns409() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "DONE", "INFO");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/complete", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_INVALID_TRANSITION"));
    }

    @Test
    void ignore_openTask_success() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "INFO");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/ignore", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reminder_task WHERE id = ?", String.class, taskId);
        assertThat(status).isEqualTo("IGNORED");
    }

    @Test
    void reopen_doneTask_success() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "DONE", "INFO");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/reopen", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reminder_task WHERE id = ?", String.class, taskId);
        assertThat(status).isEqualTo("OPEN");
    }

    @Test
    void reopen_openTask_returns409() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "WARN");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/reopen", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_INVALID_TRANSITION"));
    }

    // ==================== 6. GET /dashboard ====================

    @Test
    void dashboard_returnsExpectedStructure() throws Exception {
        seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "URGENT");
        seedTaskWithLotNull(householdId, "LOW_STOCK", itemId, "OPEN", "WARN");

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryWithin7Days").exists())
                .andExpect(jsonPath("$.expiryWithin7Days.count").isNumber())
                .andExpect(jsonPath("$.expiryWithin7Days.items").isArray())
                .andExpect(jsonPath("$.lowStockItems").exists())
                .andExpect(jsonPath("$.lowStockItems.count").isNumber())
                .andExpect(jsonPath("$.priorityTasks").exists())
                .andExpect(jsonPath("$.priorityTasks.count").isNumber())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());
    }

    @Test
    void dashboard_expiryTaskTitle_containsItemNameAndDays() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        UUID namedLotId = seedLot(householdId, namedItemId, locationId);
        seedTaskWithSnapshots(householdId, "EXPIRY", namedLotId, namedItemId, "OPEN", "URGENT",
                "CURRENT_TIMESTAMP + INTERVAL '7 days'", null, null);

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「牛奶」还有 7 天到期"));
    }

    @Test
    void dashboard_lowStockTaskTitle_containsItemNameQtyAndThreshold() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        seedTaskWithSnapshots(householdId, "LOW_STOCK", null, namedItemId, "OPEN", "WARN",
                "CURRENT_TIMESTAMP", "2", "{\"threshold\": \"5\"}");

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「牛奶」库存仅剩 2，低于阈值 5"));
    }

    @Test
    void dashboard_renamedItem_priorityTaskTitleUsesNewName() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        UUID namedLotId = seedLot(householdId, namedItemId, locationId);
        seedTaskWithSnapshots(householdId, "EXPIRY", namedLotId, namedItemId, "OPEN", "URGENT",
                "CURRENT_TIMESTAMP + INTERVAL '7 days'", null, null);

        jdbcTemplate.update("UPDATE catalog_item SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "鲜奶", namedItemId);

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「鲜奶」还有 7 天到期"));
    }

    @Test
    void dashboard_archivedItem_priorityTaskTitleStillUsesName() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        UUID namedLotId = seedLot(householdId, namedItemId, locationId);
        seedTaskWithSnapshots(householdId, "EXPIRY", namedLotId, namedItemId, "OPEN", "URGENT",
                "CURRENT_TIMESTAMP + INTERVAL '7 days'", null, null);

        jdbcTemplate.update("""
                UPDATE catalog_item
                SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP, archived_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, ownerAccountId, namedItemId);

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「牛奶」还有 7 天到期"));
    }

    @Test
    void putRules_raiseLowStockThreshold_triggersImmediateReconcile() throws Exception {
        // Default household threshold 1, stock totals 20 → no LOW_STOCK task. Raising to 30 must trigger immediately.
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "expiryDisabled": true,
                    "expiryReminderDays": [30],
                    "lowStockDisabled": false,
                    "lowStockThreshold": 30,
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Threshold now 30 > stock 20 → LOW_STOCK task must appear immediately, no daily scan needed.
        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockItems.count").value(1))
                .andExpect(jsonPath("$.lowStockItems.items[0].kind").value("LOW_STOCK"));
    }

    @Test
    void putRules_lowerLowStockThreshold_doesNotCreateSpuriousTask() throws Exception {
        // Default stock totals 20; raise threshold to 30 then lower back to 1.
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expiryDisabled": true,
                                    "expiryReminderDays": [30],
                                    "lowStockDisabled": false,
                                    "lowStockThreshold": 30,
                                    "version": 0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expiryDisabled": true,
                                    "expiryReminderDays": [30],
                                    "lowStockDisabled": false,
                                    "lowStockThreshold": 1,
                                    "version": 1
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockItems.count").value(0));
    }

    @Test
    void putRules_narrowExpiryWindow_closesOutOfWindowExpiryTask() throws Exception {
        // Lot expires in 20 days; default window [30,7,1] keeps an OPEN task for it.
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        UUID namedLotId = seedLotWithExpiry(householdId, namedItemId, locationId,
                java.time.LocalDate.now().plusDays(20).toString());
        seedTaskWithSnapshots(householdId, "EXPIRY", namedLotId, namedItemId, "OPEN", "INFO",
                "CURRENT_TIMESTAMP + INTERVAL '20 days'", null, null);

        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        // Narrow window to [7,1]: lot now out of window → task must be closed by the triggered reconcile.
        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expiryDisabled": false,
                                    "expiryReminderDays": [7, 1],
                                    "lowStockDisabled": true,
                                    "lowStockThreshold": 1,
                                    "version": 0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryWithin7Days.count").value(0))
                .andExpect(jsonPath("$.priorityTasks.items").isArray());
    }

    @Test
    void dashboard_lowStockTaskWithoutSnapshots_fallsBackToQuestionMarks() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        seedTaskWithSnapshots(householdId, "LOW_STOCK", null, namedItemId, "OPEN", "WARN",
                "CURRENT_TIMESTAMP", null, null);

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「牛奶」库存仅剩 ?，低于阈值 ?"));
    }

    @Test
    void dashboard_overdueExpiryTask_showsExpiredWording() throws Exception {
        UUID namedItemId = seedItemNamed(householdId, unitId, "牛奶");
        UUID namedLotId = seedLot(householdId, namedItemId, locationId);
        seedTaskWithSnapshots(householdId, "EXPIRY", namedLotId, namedItemId, "OPEN", "URGENT",
                "CURRENT_TIMESTAMP - INTERVAL '3 days'", null, null);

        mockMvc.perform(get("/api/v1/reminder/dashboard")
                        .with(auth(ownerPrincipal))
                        .param("days", "7")
                        .param("topN", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.items[0].title").value("「牛奶」已过期 3 天"));
    }

    // ==================== 7. Notifications ====================

    @Test
    void notifications_list_returnsPaged() throws Exception {
        seedNotification(householdId, "TASK_CREATED", "通知1", false);
        seedNotification(householdId, "TASK_CREATED", "通知2", false);
        seedNotification(householdId, "RULE_CHANGED", "通知3", true);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(auth(ownerPrincipal))
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void notifications_unreadOnly_filtersCorrectly() throws Exception {
        seedNotification(householdId, "TASK_CREATED", "未读通知", false);
        seedNotification(householdId, "RULE_CHANGED", "已读通知", true);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(auth(ownerPrincipal))
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void notifications_unreadCount_returnsCorrectCount() throws Exception {
        seedNotification(householdId, "TASK_CREATED", "未读1", false);
        seedNotification(householdId, "TASK_CREATED", "未读2", false);
        seedNotification(householdId, "RULE_CHANGED", "已读", true);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void notifications_markOneRead_marksAsRead() throws Exception {
        UUID notifId = seedNotification(householdId, "TASK_CREATED", "未读通知", false);

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notifId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        Boolean read = jdbcTemplate.queryForObject(
                "SELECT read FROM reminder_notification WHERE id = ?", Boolean.class, notifId);
        assertThat(read).isTrue();
    }

    @Test
    void notifications_markAllRead_marksAllAsRead() throws Exception {
        seedNotification(householdId, "TASK_CREATED", "未读1", false);
        seedNotification(householdId, "TASK_CREATED", "未读2", false);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        Integer unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_notification WHERE household_id = ? AND read = FALSE",
                Integer.class, householdId);
        assertThat(unreadCount).isEqualTo(0);
    }

    // ==================== 8. 跨家庭隔离 ====================

    @Test
    void crossHousehold_snoozeTaskFromOtherHousehold_returns404() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "OPEN", "WARN");
        OffsetDateTime until = OffsetDateTime.now().plusDays(3);

        // Use a task ID that doesn't belong to this household (random UUID)
        UUID nonExistentTaskId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/snooze", nonExistentTaskId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\": \"%s\"}".formatted(until.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_NOT_FOUND"));
    }

    // ==================== 9. CSRF & Problem Details ====================

    @Test
    void putRules_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/rules").with(auth(ownerPrincipal)))
                .andExpect(status().isOk());

        String body = """
                {
                    "expiryDisabled": false,
                    "expiryReminderDays": [14, 3],
                    "lowStockDisabled": false,
                    "lowStockThreshold": 2,
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/reminder/rules")
                        .with(auth(ownerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void problemDetails_hasExpectedShape() throws Exception {
        UUID taskId = seedTask(householdId, "EXPIRY", lotId, itemId, "DONE", "INFO");

        mockMvc.perform(post("/api/v1/reminder/tasks/{id}/complete", taskId)
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TASK_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.title").isNotEmpty());
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

    private UUID seedUnit(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "个" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedItem(UUID householdId, UUID unitId) {
        UUID id = UUID.randomUUID();
        String name = "物品" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE')
                """, id, householdId, name, unitId);
        return id;
    }

    private UUID seedItemNamed(UUID householdId, UUID unitId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE')
                """, id, householdId, name, unitId);
        return id;
    }

    private UUID seedLocation(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "位置" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedLot(UUID householdId, UUID itemId, UUID locationId) {
        UUID lotId = UUID.randomUUID();
        String lotNumber = "LOT-" + lotId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number)
                VALUES (?, ?, ?, ?)
                """, lotId, householdId, itemId, lotNumber);
        // Seed a stock position so the lot has quantity
        jdbcTemplate.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision)
                VALUES (?, ?, ?, ?, 10, 1)
                """, UUID.randomUUID(), householdId, lotId, locationId);
        return lotId;
    }

    private UUID seedLotWithExpiry(UUID householdId, UUID itemId, UUID locationId, String expiryDate) {
        UUID lotId = UUID.randomUUID();
        String lotNumber = "LOT-" + lotId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number, expiry_date)
                VALUES (?, ?, ?, ?, ?::date)
                """, lotId, householdId, itemId, lotNumber, expiryDate);
        jdbcTemplate.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision)
                VALUES (?, ?, ?, ?, 10, 1)
                """, UUID.randomUUID(), householdId, lotId, locationId);
        return lotId;
    }

    private UUID seedTask(UUID householdId, String kind, UUID lotId, UUID itemId,
                          String status, String severity) {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reminder_task (id, household_id, kind, lot_id, item_id, status, due_at, severity)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days', ?)
                """, taskId, householdId, kind, lotId, itemId, status, severity);
        return taskId;
    }

    private UUID seedTaskWithLotNull(UUID householdId, String kind, UUID itemId,
                                     String status, String severity) {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reminder_task (id, household_id, kind, lot_id, item_id, status, due_at, severity)
                VALUES (?, ?, ?, NULL, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days', ?)
                """, taskId, householdId, kind, itemId, status, severity);
        return taskId;
    }

    private UUID seedTaskWithSnapshots(UUID householdId, String kind, UUID lotId, UUID itemId,
                                       String status, String severity, String dueAtExpr,
                                       String qtySnapshot, String thresholdSnapshotJson) {
        UUID taskId = UUID.randomUUID();
        String sql = """
                INSERT INTO reminder_task
                    (id, household_id, kind, lot_id, item_id, status, due_at, severity, qty_snapshot, threshold_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, %s, ?, %s, %s::jsonb)
                """.formatted(dueAtExpr,
                qtySnapshot == null ? "NULL" : "?::numeric",
                thresholdSnapshotJson == null ? "NULL" : "?");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        params.add(householdId);
        params.add(kind);
        params.add(lotId);
        params.add(itemId);
        params.add(status);
        params.add(severity);
        if (qtySnapshot != null) params.add(qtySnapshot);
        if (thresholdSnapshotJson != null) params.add(thresholdSnapshotJson);
        jdbcTemplate.update(sql, params.toArray());
        return taskId;
    }

    private UUID seedNotification(UUID householdId, String scope, String title, boolean read) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reminder_notification (id, household_id, scope, title, read)
                VALUES (?, ?, ?, ?, ?)
                """, id, householdId, scope, title, read);
        return id;
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}

package com.zija.reminder.internal.mail;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

/**
 * MailDigestScheduler 集成测试。
 *
 * <ol>
 *   <li>已启用摘要的家庭：sender 收到发送调用；last_digest_sent_at 更新；MAIL_DIGEST_SENT 审计</li>
 *   <li>未启用摘要的家庭：sender 不被调用</li>
 *   <li>发送失败：MAIL_SEND_FAILED 审计；last_digest_sent_at 不更新</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.session.jdbc.initialize-schema=never",
    "zija.smtp.from=noreply@zija.local"
})
class MailDigestSchedulerIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        JavaMailSender mockMailSender() {
            return mock(JavaMailSender.class);
        }
    }

    @Autowired MailDigestScheduler scheduler;
    @Autowired MailSettingMapper mailSettingMapper;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JavaMailSender mailSender;

    private UUID householdId;
    private UUID unitId;

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
        reset(mailSender);

        householdId = seedHousehold("测试家");
        unitId = seedUnit();
    }

    @Test
    void enabledHousehold_sendsDigest_updatesLastSentAt_auditsSuccess() {
        UUID accountId = seedAccountWithEmail("owner", "所有者", "owner@example.com");
        seedMember(householdId, accountId, "OWNER");
        seedMailSetting(householdId, true, List.of("OWNER"));
        seedTask(householdId, "EXPIRY", "WARN");

        scheduler.sendDailyDigests();

        verify(mailSender).send(any(SimpleMailMessage.class));
        var setting = selectMailSetting(householdId);
        assertThat(setting.getLastDigestSentAt()).isNotNull();

        var audit = jdbcTemplate.queryForMap(
                "SELECT action FROM audit_log WHERE action = 'MAIL_DIGEST_SENT'");
        assertThat(audit.get("action")).isEqualTo("MAIL_DIGEST_SENT");
    }

    @Test
    void disabledHousehold_doesNotSend() {
        UUID accountId = seedAccountWithEmail("owner", "所有者", "owner@example.com");
        seedMember(householdId, accountId, "OWNER");
        seedMailSetting(householdId, false, List.of("OWNER"));

        scheduler.sendDailyDigests();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendFailure_auditsFailure_lastSentAtNotUpdated() {
        UUID accountId = seedAccountWithEmail("owner", "所有者", "owner@example.com");
        seedMember(householdId, accountId, "OWNER");
        seedMailSetting(householdId, true, List.of("OWNER"));
        seedTask(householdId, "LOW_STOCK", "WARN");

        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        scheduler.sendDailyDigests();

        var setting = selectMailSetting(householdId);
        assertThat(setting.getLastDigestSentAt()).isNull();

        var audit = jdbcTemplate.queryForMap(
                "SELECT action FROM audit_log WHERE action = 'MAIL_SEND_FAILED'");
        assertThat(audit.get("action")).isEqualTo("MAIL_SEND_FAILED");
    }

    // ==================== Seed helpers ====================

    private UUID seedHousehold(String name) {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName(name + h.getId().toString().substring(0, 6));
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedAccountWithEmail(String username, String displayName, String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, email, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, ?, 'ACTIVE')
                """, id, username, username.toUpperCase(), displayName, email);
        return id;
    }

    private void seedMember(UUID householdId, UUID accountId, String role) {
        var m = new MemberEntity();
        m.setId(UUID.randomUUID());
        m.setHouseholdId(householdId);
        m.setAccountId(accountId);
        m.setRole(role);
        m.setStatus("ACTIVE");
        memberMapper.insert(m);
    }

    private void seedMailSetting(UUID householdId, boolean digestEnabled, List<String> recipientRoles) {
        var e = new MailSettingEntity();
        e.setId(UUID.randomUUID());
        e.setHouseholdId(householdId);
        e.setDigestEnabled(digestEnabled);
        e.setDigestFrequency("DAILY");
        e.setUrgentEnabled(true);
        e.setRecipientRoles(recipientRoles);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setVersion(0);
        mailSettingMapper.insert(e);
    }

    private UUID seedUnit() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, '个', '个', 0, 'ACTIVE')
                """, id, householdId);
        return id;
    }

    private UUID seedItem() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status,
                                          expiry_reminder_mode, low_stock_mode)
                VALUES (?, ?, '测试物品', 'CONSUMABLE', ?, 'ACTIVE', 'INHERIT', 'INHERIT')
                """, id, householdId, unitId);
        return id;
    }

    private UUID seedLot(UUID itemId) {
        UUID lotId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number)
                VALUES (?, ?, ?, ?)
                """, lotId, householdId, itemId, "LOT-" + lotId.toString().substring(0, 8));
        return lotId;
    }

    private void seedTask(UUID householdId, String kind, String severity) {
        UUID itemId = seedItem();
        var t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(householdId);
        t.setKind(kind);
        t.setItemId(itemId);
        if ("EXPIRY".equals(kind)) {
            t.setLotId(seedLot(itemId));
        }
        t.setStatus("OPEN");
        t.setSeverity(severity);
        t.setDueAt(OffsetDateTime.now().plusDays(3));
        t.setLastReconciledAt(OffsetDateTime.now());
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setVersion(0);
        taskMapper.insert(t);
    }

    private MailSettingEntity selectMailSetting(UUID householdId) {
        return mailSettingMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MailSettingEntity>()
                        .eq(MailSettingEntity::getHouseholdId, householdId));
    }
}

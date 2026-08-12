package com.zija.reminder.internal.mail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.household.HouseholdApi;
import com.zija.reminder.internal.persistence.TaskMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 每日提醒摘要邮件调度器。
 * <p>
 * 定时任务（默认每天 03:30）遍历所有启用摘要的家庭，渲染摘要邮件并发送给符合条件的成员。
 * 发送失败时写入 MAIL_SEND_FAILED 审计事件，不阻塞其他家庭的处理。
 */
@Service
class MailDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailDigestScheduler.class);

    private final MailSettingMapper mailSettingMapper;
    private final MailService mailService;
    private final MailTemplateRenderer templateRenderer;
    private final HouseholdApi householdApi;
    private final TaskMapper taskMapper;
    private final SystemApi systemApi;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public MailDigestScheduler(MailSettingMapper mailSettingMapper, MailService mailService,
                               MailTemplateRenderer templateRenderer, HouseholdApi householdApi,
                               TaskMapper taskMapper, SystemApi systemApi,
                               JdbcTemplate jdbcTemplate, @org.springframework.beans.factory.annotation.Qualifier("reminderClock") Clock clock) {
        this.mailSettingMapper = mailSettingMapper;
        this.mailService = mailService;
        this.templateRenderer = templateRenderer;
        this.householdApi = householdApi;
        this.taskMapper = taskMapper;
        this.systemApi = systemApi;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 生产调度：每天 03:30（Asia/Shanghai）。 */
    @Scheduled(cron = "${zija.schedule.mail-digest:0 30 3 * * *}", zone = "${zija.schedule.zone:Asia/Shanghai}")
    public void sendDailyDigests() {
        if (!mailService.isConfigured()) {
            log.debug("Mail not configured, skipping digest send");
            return;
        }

        var households = mailSettingMapper.selectList(
                new LambdaQueryWrapper<MailSettingEntity>()
                        .eq(MailSettingEntity::getDigestEnabled, true));

        for (var setting : households) {
            try {
                sendForHousehold(setting);
            } catch (RuntimeException ex) {
                log.warn("Digest send failed for household {}: {}",
                        setting.getHouseholdId(), ex.getMessage());
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        "MAIL_SEND_FAILED", "FAILURE",
                        setting.getHouseholdId(), null, null, null, null,
                        Map.of("reason", ex.getMessage() != null ? ex.getMessage() : "unknown")));
            }
        }
    }

    private void sendForHousehold(MailSettingEntity setting) {
        UUID householdId = setting.getHouseholdId();

        var householdInfo = householdApi.findHousehold();
        String householdName = householdInfo.map(h -> h.name()).orElse("知家");

        List<String> emails = findMemberEmails(householdId, setting.getRecipientRoles());
        if (emails.isEmpty()) {
            log.debug("No recipient emails for household {}, skipping", householdId);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime to = now.plusDays(7);
        var expiryTasks = taskMapper.expiryWithinDays(householdId, now, to, now, 50);
        var lowStockTasks = taskMapper.lowStockOpenTasks(householdId, now, 50);

        List<Map<String, String>> expiryModels = expiryTasks.stream()
                .map(t -> Map.of("title", "临期任务", "dueAt",
                        t.getDueAt() != null ? t.getDueAt().toLocalDate().toString() : ""))
                .toList();
        List<Map<String, String>> lowStockModels = lowStockTasks.stream()
                .map(t -> Map.of("title", "低库存任务", "dueAt",
                        t.getDueAt() != null ? t.getDueAt().toLocalDate().toString() : ""))
                .toList();

        String html = templateRenderer.renderDigest(Map.of(
                "householdName", householdName,
                "expiryTasks", expiryModels,
                "lowStockTasks", lowStockModels,
                "link", ""));

        for (String email : emails) {
            boolean sent = mailService.send(email, "知家 · 每日提醒摘要", html);
            if (!sent) {
                throw new RuntimeException("SMTP send failed to " + email);
            }
        }

        setting.setLastDigestSentAt(now);
        setting.setUpdatedAt(now);
        mailSettingMapper.updateById(setting);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "MAIL_DIGEST_SENT", "SUCCESS",
                householdId, null, null, null, null,
                Map.of("recipients", String.valueOf(emails.size()))));

        log.info("Digest sent for household {} to {} recipients", householdId, emails.size());
    }

    /**
     * 通过 JdbcTemplate 查询符合角色条件的家庭成员邮箱。
     * <p>
     * reminder 模块不允许依赖 identity 模块，因此直接查询 account 表。
     */
    List<String> findMemberEmails(UUID householdId, List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of();

        String placeholders = roles.stream().map(r -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT a.email FROM member m
                JOIN account a ON a.id = m.account_id
                WHERE m.household_id = ? AND m.role IN (%s) AND m.status = 'ACTIVE'
                  AND a.email IS NOT NULL AND a.email != ''
                """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(householdId);
        params.addAll(roles);

        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }
}

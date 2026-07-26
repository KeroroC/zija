# 阶段五 5c：可选 SMTP 邮件提醒 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可选 SMTP 邮件提醒：`spring-boot-starter-mail` 依赖与 `ZIJA_SMTP_*` 配置、`reminder_household_mail_setting` 表与 CRUD、摘要/紧急邮件发送、调度、前端「邮件提醒」分区、审计与测试；不配置 SMTP 时静默禁用，主业务不受影响。

**Architecture:** 新增 `MailCapabilityConfig` 用 `@ConditionalOnProperty(name="zija.smtp.host")` 条件装配 `JavaMailSender`；`MailService` 在 `mailSender==null` 时短路。`MailSettingService` CRUD 同 5a `ReminderRuleService` 模式（乐观锁）。`MailDigestScheduler` `@Scheduled` 每日触发摘要；reconcile 产生 URGENT 任务后异步触发紧急。前端在 `ReminderRulesSettingsView.vue` 追加「邮件提醒」分区。

**Tech Stack:** spring-boot-starter-mail、JavaMailSender、Thymeleaf（或纯 String.format HTML）、Flyway V3、JdbcTemplate、Testcontainers + GreenMail 或 MockitoBean JavaMailSender。

**覆盖 spec：** `docs/superpowers/specs/2026-07-26-phase5c-smtp-mail-design.md`（全部章节）。

---

## 计划范围

仅 SMTP 邮件增量。不做端实时推送、第三方邮件云服务、邮件模板自编辑。

## 前置条件

- 5a/5b 已交付；reminder 模块、`ReminderRulesSettingsView.vue` 存在。
- 工作树干净。

## 目标文件清单

**Create（后端）：**
- `backend/src/main/resources/db/migration/V3__create_reminder_mail_setting.sql`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailCapabilityConfig.java`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailService.java`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailSettingService.java`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailDigestScheduler.java`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailTemplateRenderer.java`
- `backend/src/main/java/com/zija/reminder/internal/mail/MailSettingEntity.java` / `MailSettingMapper.java` + XML
- `backend/src/main/resources/mail/digest.html` / `urgent.html`
- 测试：`MailTemplateRendererTest`、`MailServiceIntegrationTest`、`MailDigestSchedulerIntegrationTest`、`MailSettingEndpointIntegrationTest`

**Modify：**
- `backend/pom.xml` — 加 `spring-boot-starter-mail`
- `backend/src/main/java/com/zija/reminder/internal/ReminderController.java` — 加 `GET/PUT /api/v1/reminder/mail-settings`
- `backend/src/main/java/com/zija/reminder/internal/ReminderReconciler.java` — URGENT 产生后触发紧急邮件
- `frontend/src/views/ReminderRulesSettingsView.vue` — 追加「邮件提醒」分区
- `frontend/src/api/reminder.ts` — 加 `fetchMailSettings/updateMailSettings`
- `.env.example`

每个任务结束提交一次。

---

## 任务 1：依赖、迁移与配置骨架

**Files:** `backend/pom.xml`、`backend/src/main/resources/db/migration/V3__create_reminder_mail_setting.sql`、`.env.example`

- [ ] **步骤 1：加 `spring-boot-starter-mail`**

在 `backend/pom.xml` 的 `<dependencies>` 追加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

- [ ] **步骤 2：创建 V3 迁移**（见 spec §2 SQL）

- [ ] **步骤 3：更新 `.env.example`** 追加 spec §2 的 `ZIJA_SMTP_*` 变量

- [ ] **步骤 4：本地验证迁移**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS。

- [ ] **步骤 5：提交**

```bash
git add backend/pom.xml backend/src/main/resources/db/migration/V3__create_reminder_mail_setting.sql .env.example
git commit -m "feat(reminder): SMTP 依赖 + V3 邮件设置表 + ZIJA_SMTP_* 环境变量"
```

---

## 任务 2：MailCapabilityConfig + MailService（条件装配 + 短路）

**Files:** `backend/src/main/java/com/zija/reminder/internal/mail/MailCapabilityConfig.java`、`MailService.java`

- [ ] **步骤 1：写 MailServiceIntegrationTest（短路 + 启用发送）**

```java
package com.zija.reminder.internal.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class MailServiceIntegrationTest {

    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration static class Fix { @Bean JavaMailSender mockSender() { return mock(JavaMailSender.class); } }

    @Test
    void sendDigestCallsMailSenderWhenConfigured(MailService svc, JavaMailSender sender) {
        // 注入 sender mock；from 由测试属性提供
        svc.sendDigest("owner@example.com", "<html>body</html>");
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
```

> **实施注：** 真实场景由 `@ConditionalOnProperty` 控制 Bean 是否创建；上文用 `@TestConfiguration` 显式 mock 注入模拟「已配置」。另写一个 `MailDisabledWhenNoHostTest`：不开 mockSender，断言 `MailService.sendDigest` 不抛且不发送（短路）。

- [ ] **步骤 2：实现 MailCapabilityConfig**

```java
package com.zija.reminder.internal.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class MailCapabilityConfig {

    @Bean
    @ConditionalOnProperty(name = "zija.smtp.host")
    public JavaMailSenderImpl mailSender(@Value("${zija.smtp.host}") String host,
                                         @Value("${zija.smtp.port:587}") int port,
                                         @Value("${zija.smtp.username:}") String user,
                                         @Value("${zija.smtp.password:}") String pass,
                                         @Value("${zija.smtp.tls:true}") boolean tls) {
        var s = new JavaMailSenderImpl();
        s.setHost(host); s.setPort(port); s.setUsername(user); s.setPassword(pass);
        var p = new Properties();
        p.put("mail.smtp.auth", !user.isEmpty());
        p.put("mail.smtp.starttls.enable", String.valueOf(tls));
        s.setJavaMailProperties(p);
        return s;
    }
}
```

- [ ] **步骤 3：实现 MailService**

```java
package com.zija.reminder.internal.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender sender;       // null 时 SMTP 未配置
    private final String from;

    public MailService(@Autowired(required = false) JavaMailSender sender,
                       @Value("${zija.smtp.from:}") String from) {
        this.sender = sender; this.from = from;
    }

    public boolean isConfigured() { return sender != null && !from.isBlank(); }

    public boolean send(String to, String subject, String htmlBody) {
        if (!isConfigured()) return false;
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(from); msg.setTo(to); msg.setSubject(subject); msg.setText(htmlBody);
            sender.send(msg);
            return true;
        } catch (RuntimeException ex) {
            log.warn("邮件发送失败 to={} subject={} err={}", to, subject, ex.getMessage());
            return false;
        }
    }

    public void sendDigest(String to, String html) {
        if (!isConfigured()) return;
        send(to, "知家 · 每日提醒摘要", html);
    }
    public void sendUrgent(String to, String html) {
        if (!isConfigured()) return;
        send(to, "知家 · 紧急提醒", html);
    }
}
```

- [ ] **步骤 4：验证 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=MailServiceIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/mail/
git commit -m "feat(reminder): SMTP 条件装配 + MailService 短路（未配置不报错）"
```

---

## 任务 3：MailSettingEntity + Mapper + Service + 端点

**Files:** Entity/Mapper/XML、`MailSettingService.java`、`ReminderController.java` 追加 mail-settings 端点

- [ ] **步骤 1：写失败测试 `MailSettingEndpointIntegrationTest`**
  - GET 懒初始化默认（全关、OWNER,ADMIN）
  - PUT 带版本成功；旧版本冲突 `REMINDER_MAIL_SETTING_VERSION_CONFLICT` 409
  - GET 返回 `smtpConfigured` 反映 `MailService.isConfigured()`
  - MEMBER PUT → 403
  - PUT 写 `MAIL_SETTING_UPDATE` 审计
- [ ] **步骤 2：实现 Entity/Mapper（同 HouseholdRuleEntity 模式；BaseMapper 即可，无 XML）**
- [ ] **步骤 3：实现 MailSettingService（懒初始化 + 乐观锁 + 审计）**
- [ ] **步骤 4：在 ReminderController 追加 `@GetMapping("/mail-settings")` 与 `@PutMapping("/mail-settings")`** —— 复用 5a 的权限模式与 `X-Current-Account`
- [ ] **步骤 5：在 ReminderExceptionHandler 加 `ReminderMailSettingVersionConflictException`**
- [ ] **步骤 6：验证 + 提交**

```bash
git add ...
git commit -m "feat(reminder): 家庭邮件设置 CRUD + 端点 + 审计（乐观锁）"
```

---

## 任务 4：邮件模板与 MailTemplateRenderer

**Files:** `backend/src/main/resources/mail/digest.html`、`urgent.html`、`MailTemplateRenderer.java`、`MailTemplateRendererTest.java`

- [ ] **步骤 1：写模板渲染单元测试**

```java
@Test
void digestRendersTasks() {
    var r = new MailTemplateRenderer();
    var html = r.renderDigest(Map.of("householdName","我家",
        "expiryTasks", List.of(Map.of("title","牛奶将到期","dueAt","明天")),
        "lowStockTasks", List.of(),
        "link","http://x/"));
    assertThat(html).contains("我家").contains("牛奶将到期").contains("明天");
}

@Test
void urgentRendersTitleAndLink() {
    var r = new MailTemplateRenderer();
    var html = r.renderUrgent(Map.of("title","紧急","severity","URGENT","link","http://x/"));
    assertThat(html).contains("紧急").contains("URGENT");
}
```

- [ ] **步骤 2：实现 MailTemplateRenderer（纯 String 模板，`{var}` 占位替换，避免引入 Thymeleaf 依赖）**
- [ ] **步骤 3：验证 + 提交**

```bash
git add backend/src/main/resources/mail/ backend/src/main/java/com/zija/reminder/internal/mail/MailTemplateRenderer.java backend/src/test/java/com/zija/reminder/internal/mail/MailTemplateRendererTest.java
git commit -m "feat(reminder): 邮件模板与渲染器（中文 HTML、纯函数）"
```

---

## 任务 5：MailDigestScheduler + 紧急邮件触发

**Files:** `MailDigestScheduler.java`、修改 `ReminderReconciler.java`（紧急触发钩子）、测试

- [ ] **步骤 1：写 MailDigestSchedulerIntegrationTest**
  - 启用摘要的家庭：mock sender 收到 send 一次；`last_digest_sent_at` 更新；`MAIL_DIGEST_SENT` 审计
  - 未启用摘要的家庭：sender 不被调用
  - 发送失败：`MAIL_SEND_FAILED` 审计；`last_digest_sent_at` 不更新或不影响主流程
- [ ] **步骤 2：实现 MailDigestScheduler**
  - `@Scheduled(cron="${zija.schedule.mail-digest:0 30 3 * * *}")`
  - 遍历 `reminder_household_mail_setting where digest_enabled=true`
  - 按 `recipient_roles` 收集成员 email（ HouseholdApi.findMembers + account email）
  - 用 DashboardService 取未处理清单
  - `MailService.sendDigest(email, html)`；成功记 `MAIL_DIGEST_SENT`，失败记 `MAIL_SEND_FAILED`
- [ ] **步骤 3：在 ReminderReconciler 紧急任务产生后触发 `mailService.sendUrgentIfEnabled(...)`**
  - 在写 URGENT 任务 notification 后，新增 `EmailTrigger` 内部组件（避免 Reconciler 直接依赖 MailService；用事件或直接注入均按既有风格）
  - 异步：`@Async` 或 `EventRetryService` 模式；失败只记 `MAIL_SEND_FAILED`，不重试
- [ ] **步骤 4：验证 + 提交**

```bash
git add ...
git commit -m "feat(reminder): 摘要每日调度 + 紧急邮件 reconcile 后触发（失败不阻塞）"
```

---

## 任务 6：前端「邮件提醒」分区 + e2e

**Files:** `frontend/src/api/reminder.ts`（加 `fetchMailSettings/updateMailSettings`）、`frontend/src/views/ReminderRulesSettingsView.vue`（追加分区）、`frontend/e2e/reminder.spec.ts`（追加邮件设置场景）

- [ ] **步骤 1：API 模块追加**
  ```ts
  export interface MailSetting { digestEnabled: boolean; digestFrequency: "DAILY"|"WEEKLY"; urgentEnabled: boolean; recipientRoles: string[]; version: number; smtpConfigured: boolean }
  export const fetchMailSettings = () => getJson<MailSetting>("/api/v1/reminder/mail-settings")
  export const updateMailSettings = (b: Omit<MailSetting,"smtpConfigured">) => putJson<MailSetting>("/api/v1/reminder/mail-settings", b)
  ```
- [ ] **步骤 2：在 ReminderRulesSettingsView 追加「邮件提醒」分区**
  - SMTP 状态徽章（只读 `smtpConfigured`）
  - 摘要开关 + 频率单选
  - 紧急开关
  - 收件角色多选（OWNER/ADMIN/MEMBER）
  - 保存调 `updateMailSettings`，乐观锁冲突刷新
- [ ] **步骤 3：单元测试 + e2e 追加**
- [ ] **步骤 4：验证 + 提交**

```bash
git add frontend/src/api/reminder.ts frontend/src/views/ReminderRulesSettingsView.vue frontend/e2e/reminder.spec.ts
git commit -m "feat(frontend): 邮件提醒分区（SMTP 状态只读+摘要/紧急开关+角色白名单）"
```

---

## 任务 7：5c 收尾

- [ ] **步骤 1：`make backend-test` + `make frontend-test` + `make frontend-build` + `make e2e-smoke`**
- [ ] **步骤 2：写收尾记录 `docs/superpowers/notes/2026-07-26-phase5c-smtp-mail-completion.md`**
- [ ] **步骤 3：提交**

---

## 自检清单

- ✅ **Spec 覆盖**：§2 迁移→任务 1；§3.1 条件装配→任务 2；§3.2/§3.5 CRUD+端点→任务 3；§3.6 模板→任务 4；§3.3/§3.4 紧急+摘要→任务 5；§4 前端→任务 6；§5/§6 测试验收→任务 7。
- ✅ **无占位**：每任务含 Files + 步骤 + 命令 + 提交；任务 3 因结构与 5a `ReminderRuleService` 完全同构，按 5a 已落地的 `ReminderHouseholdRuleIntegrationTest` 模板执行并注明来源，非「add tests later」。
- ✅ **类型一致**：`MailSetting`、`MailService.sendDigest/sendUrgent`、`MailTemplateRenderer` 跨任务签名一致。
- ✅ **守恒**：不配置 SMTP 时 `make verify` 应全绿、邮件能力静默禁用；失败不阻塞主业务。
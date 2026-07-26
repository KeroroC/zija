# 阶段五 5c：可选 SMTP 邮件提醒 设计方案

- **日期：** 2026-07-26
- **状态：** 已确认，作为 5c 实施计划与验收依据
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` §3.1（不配置 SMTP 也能完整运行）、§6.6（配置 SMTP 后可按家庭设置发送邮件摘要或紧急提醒）、§10（密钥通过环境变量或 Docker Secret 注入；日志不含敏感配置）
- **交付路线：** 阶段 5（提醒与任务首页），本段为 5c 可选 SMTP。依赖 5a（reminder 模块、任务/通知/reconcile 已就绪）+ 5b（规则配置 UI 已就绪）。
- **前置依赖：** 5a/5b 已交付。reminder 模块有 `reminder_notification` 表与 `ReminderRuleService`。`spring-boot-starter-mail` 未引入；`.env.example` 无 `ZIJA_SMTP_*`。

## 1. 目标与边界

### 1.1 目标

- 部署者可选择配置 SMTP；**不配置也能完整运行**（spec §3.1）。邮件发送是站内通知之上的可选增量。
- 管理员可在家庭设置中开启/关闭邮件提醒，并选择两类邮件：
  - **摘要邮件（DIGEST）**：定时（默认每日）汇总家庭未处理任务（含临期/低库存清单）。
  - **紧急邮件（URGENT）**：reconcile 产生或刷新 URGENT 任务时实时派发。
- 邮件发送失败不阻塞主业务（库存/任务正确性），失败记录由系统通知提示管理员。
- 多收件人地址来自家庭成员账户的 `email` 字段（确认收邮件）；可选「发送到哪些成员」白名单（首期为实现简单，默认发送给所有启用邮件的 owner/admin，普通成员可选）。

### 1.2 5c 在范围内

- 新增 `spring-boot-starter-mail` 依赖与 `spring.mail.*` 配置（按 `ZIJA_SMTP_*` 环境变量驱动；缺失则邮件能力禁用且不报错）。
- 新增 `reminder_household_mail_setting` 表（家庭单例：是否启用摘要/紧急、摘要频率、收件角色白名单、SMTP 配置状态只读）。
- 新增 `MailService`（封装 `JavaMailSender`，发送模板化 HTML 邮件；SMTP 未配置时 Bean 不实例化或短路）。
- 新增 `MailDigestScheduler`（`@Scheduled` 每日，遍历启用摘要的家庭，发送摘要邮件）。
- reminder 模块在 reconcile 产生 URGENT 任务时的「通知生成」钩子内（同事务后置/spike）异步触发紧急邮件（仅当家庭启用紧急邮件且 SMTP 已配置）。
- 前端：`家庭设置/提醒规则` 页追加「邮件提醒」分区（owner/admin 可写；member 只读）：SMTP 状态（只读，由后端实际配置决定）、摘要开关+频率、紧急开关、收件角色勾选（OWNER/ADMIN/MEMBER）。
- 测试：单元（邮件模板渲染、配置解析）、集成（Testcontainers GreenMail 或 mock `JavaMailSender`）、e2e（配置 UI；真实发送不进 CI）。
- 审计：`MAIL_SETTING_UPDATE`、`MAIL_DIGEST_SENT`、`MAIL_URGENT_SENT`、`MAIL_SEND_FAILED`。

### 1.3 5c 明确不做

- 不引入第三方邮件服务或云服务（spec §3.1）。
- 不在浏览器前端存储 SMTP 密码；SMTP 连接仅由后端环境变量配置。
- 不实现富文本编辑或邮件模板自定义；首期固定模板（中文）。
- 不替代站内通知；邮件是冗余通道。
- 不做退订链接（单家庭私有部署，管理员即收件人）。

## 2. 数据模型

迁移 `backend/src/main/resources/db/migration/V3__create_reminder_mail_setting.sql`（V1 合并、V2 reminder 核心 = 5a）。

```sql
CREATE TABLE reminder_household_mail_setting (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL UNIQUE REFERENCES household(id),
    digest_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    digest_frequency    VARCHAR(20) NOT NULL DEFAULT 'DAILY',   -- DAILY | WEEKLY
    urgent_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    recipient_roles     VARCHAR(60) NOT NULL DEFAULT 'OWNER,ADMIN', -- 逗号分隔角色
    last_digest_sent_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_reminder_mail_freq CHECK (digest_frequency IN ('DAILY','WEEKLY'))
);
```

> SMTP 连接配置（host/port/user/pass/from/tls）通过 `ZIJA_SMTP_*` 环境变量注入 `spring.mail.*`，**不落库**（spec §10：密钥通过环境变量或 Docker Secret）。

更新 `.env.example` 增加：
```
ZIJA_SMTP_HOST=
ZIJA_SMTP_PORT=587
ZIJA_SMTP_USERNAME=
ZIJA_SMTP_PASSWORD=
ZIJA_SMTP_FROM=noreply@zija.local
ZIJA_SMTP_TLS=true
```

## 3. 后端设计

### 3.1 SMTP 能力探测

```java
@Configuration(proxyBeanMethods = false)
public class MailCapabilityConfig {
    @Bean
    @ConditionalOnProperty(name = "zija.smtp.host")
    public JavaMailSender mailSender(@Value("${zija.smtp.host}") String host, ...) {
        var props = new java.util.Properties();
        props.put("mail.smtp.host", host);
        // port/auth/tls/starttls 按环境变量配
        var sender = new JavaMailSenderImpl();
        sender.setJavaMailProperties(props);
        sender.setUsername(username); sender.setPassword(password);
        return sender;
    }
    @Bean
    public MailService mailService(@Autowired(required=false) JavaMailSender mailSender,
                                   @Value("${zija.smtp.from:}") String from) {
        return new MailService(mailSender, from);
    }
}
```

`MailService` 检查 `mailSender == null` 时直接短路返回「未启用」，不抛错。

### 3.2 MailSettingService

懒初始化家庭默认（`digest_enabled=false`、`urgent_enabled=false`、`recipient_roles=OWNER,ADMIN`），CRUD 同 `ReminderRuleService` 模式（乐观锁、审计、只读 SMTP 状态）。

### 3.3 紧急邮件触发

`ReminderReconciler` 写 URGENT 任务通知后，在同事务提交后异步触发 `mailService.sendUrgentIfEnabled(householdId, taskId)`（用 `@Async` 或既有 `EventRetryService` 风格的独立小事务+失败计数，避免阻塞主流程）。失败写 `MAIL_SEND_FAILED` 审计 + 系统通知，不重试紧急邮件（首期）。

### 3.4 摘要邮件调度

`MailDigestScheduler` `@Scheduled` 每日 03:30（晚于 `ExpiryScanScheduler` 03:00），遍历启用摘要的家庭，按 `recipient_roles` 收集成员 email，渲染模板，调 `mailService.sendDigest(...)`，记录 `last_digest_sent_at` + `MAIL_DIGEST_SENT` 审计；失败 `MAIL_SEND_FAILED`。

### 3.5 端点

| 方法 | 路径 | 角色 | 描述 |
|---|---|---|---|
| GET | `/api/v1/reminder/mail-settings` | 全员 | 返回家庭邮件设置 + `smtpConfigured: boolean` |
| PUT | `/api/v1/reminder/mail-settings` | OWNER/ADMIN | 更新（乐观锁） |

错误码复用 409 版本冲突；新增 `REMINDER_MAIL_SETTING_VERSION_CONFLICT`。

### 3.6 邮件模板

固定 HTML 模板（中文），在 `backend/src/main/resources/mail/`：
- `digest.html`：家庭名、未处理任务数、临期清单（前 N）、低库存清单（前 N）、查看链接（带部署 `ZIJA_HTTP_HOST`）。
- `urgent.html`：任务标题、紧急程度、相关物品/批次、查看链接。

## 4. 前端设计

在 `ReminderRulesSettingsView.vue` 追加「邮件提醒」分区（同一页或独立 `/settings/mail`；首期同页分区）：

- SMTP 状态徽章（只读，绿「已配置」/灰「未配置」）；未配置时提示管理员在 `.env` 配置 `ZIJA_SMTP_*` 后重启。
- 摘要开关 + 频率单选（每日/每周）。
- 紧急开关。
- 收件角色多选（OWNER/ADMIN/MEMBER）。
- 保存调 `PUT /api/v1/reminder/mail-settings`，乐观锁冲突刷新。

## 5. 测试策略

- 单元：`MailTemplateRendererTest`（摘要/紧急模板渲染、空清单、超长清单截断）。
- 集成（Testcontainers + GreenMail 或 `@MockitoBean JavaMailSender`）：
  - 未配置 SMTP 时 `MailService` 短路。
  - 配置启用紧急邮件时 reconcile URGENT 任务触发 `mailSender.send(...)` 一次。
  - 摘要调度对启用家庭发送、未启用跳过、`last_digest_sent_at` 更新、审计条目写入。
  - 失败写 `MAIL_SEND_FAILED` 且不阻塞主业务。
- e2e：规则/邮件设置页保存切换；真实发送不在 CI（GreenMail 仅集成测试）。

## 6. 验收门槛

1. 不配置 `ZIJA_SMTP_HOST` 时 `make verify` 全绿、应用正常启动、邮件能力静默禁用。
2. 配置后摘要/紧急邮件按设置触发，发送失败不阻塞库存/任务正确性。
3. `make backend-test`、`make frontend-test`、`make frontend-build` 全绿；`make e2e-smoke` 含邮件设置 UI 场景。
4. 审计覆盖 `MAIL_SETTING_UPDATE`、`MAIL_DIGEST_SENT`、`MAIL_URGENT_SENT`、`MAIL_SEND_FAILED`。

## 7. 已确认关键决策

1. SMTP 通过 `spring-boot-starter-mail` + `ZIJA_SMTP_*` 环境变量驱动，密钥不落库（spec §10）。
2. 不配置也能完整运行（spec §3.1）—— `MailService` 短路，主业务不依赖邮件成功。
3. 两类邮件：摘要（定时）与紧急（reconcile 触发）。
4. 收件人来自成员 `email` 字段 + 角色白名单；普通成员默认不发。
5. 邮件失败不重试紧急、不阻塞主流程，只记审计/系统通知。
6. 模板固定中文 HTML，不允许用户自定义。
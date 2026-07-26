# 阶段五 5a：提醒与任务首页 后端 设计方案

- **日期：** 2026-07-26
- **状态：** 已确认，作为 5a 实施计划与验收依据
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` §6.6（提醒与任务）、§6.7（首页方向，仅后端聚合部分）、§8.5（事务提交后可靠投递）、§12.1（事件测试：失败可重试且不重复处理）
- **交付路线：** 阶段 5（提醒与任务首页），本段为 5a 后端。5a 不依赖 5b 前端，可独立验收。
- **前置依赖：** 阶段 1–4 已交付（`system`/`identity`/`household`/`catalog`/`location`/`file`/`inventory` 模块，迁移已合并为单一 V1__create_all_tables.sql）。`StockChangedEvent` 已由阶段四发布（同步桩），公开 `InventoryApi`/`CatalogApi` 已存在。

## 1. 模块边界与范围

新增 Spring Modulith 模块 `reminder`，`allowedDependencies = { household, catalog, inventory, system }`：订阅 `inventory` 公开事件、调 `catalog`/`inventory` 只读 Api、`household` 取成员与角色、`system` 记审计与发系统通知。依赖方向单向；反向不依赖。

模块结构沿用既有约定：

```
com.zija.reminder/
  ReminderApi.java              # 公开只读端口（dashboard 聚合、任务列表只读 DTO）——供未来 reporting 复用
  package-info.java             # @ApplicationModule
  internal/
    ReminderController.java
    ReminderService.java         # 规则读写、任务列表、状态机操作编排
    ReminderReconciler.java       # reconcile(affectedLotId, affectedItemId) 单一入口，事件/定时共用
    ReminderRuleResolver.java     # 物品生效规则解析（纯函数）
    SeverityClassifier.java       # 临期/低库存 severity 档位（纯函数）
    ReminderEventListener.java   # @ApplicationModuleListener(AFTER_COMMIT) 消费 StockChangedEvent
    EventRetryService.java        # dead-letter + @Scheduled 重投
    ExpiryScanScheduler.java      # @Scheduled crontab daily 全量临期扫描 → 复用 Reconciler
    ReminderExceptionHandler.java
    ClockConfig.java              # reminder 模块内 Clock bean
    persistence/ (Entity, Mapper, XML)
```

### 1.1 5a 范围

- `reminder_household_rule` 家庭单例表，默认值懒初始化（临期 30/7/1 天、低库存 1 单位），管理员可改或禁用。
- `reminder_task` 表，单表 + kind（EXPIRY/LOW_STOCK），未完状态唯一合并，保留 DONE/IGNORED 历史，四态状态机 OPEN/SNOOZED/DONE/IGNORED + snoozed_until + due_at + severity + threshold_snapshot + qty_snapshot。
- `reminder_notification` 站内通知表，任务产生/状态变更/规则变更联动写一条。
- `reminder_processed_event` 事件去重表（event_id 唯一）+ `reminder_event_dead_letter`（failure_count/next_retry_at/last_error/abandoned）重投。
- 可靠事件投递：改造 `inventory` 的 `InventoryEventPublisher` 为 Spring Modulith 事务内登记 + 提交后派发；reminder 侧 `@ApplicationModuleListener(AFTER_COMMIT)` 处理；失败写 dead-letter，`@Scheduled` 扫表重投；去重保证「恰好一次效果」。
- 每日 `@Scheduled`（按部署时区 ~03:00）全量临期扫描，复用同一 `Reconciler` 与去重机制；同时刷新 SNOOZED 且 snoozed_until<=now 的任务回 OPEN。
- 扩展 `CatalogApi.ItemInfo`（+4 个 reminder 字段）与 `InventoryApi`（+`lotsOfItem`、`currentTotalStockOfItem` + LotInfo record），仅追加，不改既有契约语义。
- reminder 模块内 `Clock` bean（`Clock.systemUTC()`），测试可注入。
- 端点：`GET/PUT /api/v1/reminder/rules`、`GET /api/v1/reminder/tasks`（分页+筛选+按紧急度排序）、`POST .../tasks/{id}/{snooze,complete,ignore,reopen}`、`GET /api/v1/reminder/dashboard`。
- 通知端点：`GET /api/v1/notifications`（分页+unread 过滤）、`GET /api/v1/notifications/unread-count`、`POST /api/v1/notifications/{id}/read`、`POST /api/v1/notifications/read-all`。
- 审计新动作：`REMINDER_RULE_UPDATE`、`REMINDER_TASK_SNOOZED`、`REMINDER_TASK_COMPLETED`、`REMINDER_TASK_IGNORED`、`REMINDER_TASK_REOPENED`。
- Testcontainers 全覆盖 + `ModularityTests` 扩充 reminder 断言 + OpenApi 契约基线升至 V2。

### 1.2 5a 明确不做（守恒）

- 前端任何 `.vue` / `.ts` / 路由 / 侧边栏启用（提醒中心仍 disabled）——属 5b。
- SMTP 发送、`spring-boot-starter-mail` 依赖、`ZIJA_SMTP_*` 变量——属 5c。
- 报表、CSV、全局搜索——属阶段六。
- 字段级 UI 配置家庭默认规则——属 5b。
- 库存写命令或 catalog 写命令的语义改动；只追加公开 Api 字段/方法。
- ModularityTests 之外不引入新模块对 inventory/catalog 的反向依赖。
- 不引入 Kafka/Rabbit 等消息中间件（spec §3.3 明确不做）。

## 2. 数据模型与迁移

迁移文件 `backend/src/main/resources/db/migration/V2__create_reminder_core.sql`（V1 已包含全部既有表；5a 新增 V2）。仅前进、幂等安全、不改既有表（catalog/inventory/household 表不动；CatalogApi/InventoryApi 扩展是 Java 侧追加，不动表）。

```sql
-- 1) 家庭默认提醒规则（家庭单例）
CREATE TABLE reminder_household_rule (
    id                    UUID PRIMARY KEY,
    household_id          UUID NOT NULL UNIQUE REFERENCES household(id),
    expiry_disabled       BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_reminder_days  SMALLINT[] NOT NULL DEFAULT ARRAY[30,7,1]::SMALLINT[],
    low_stock_disabled    BOOLEAN NOT NULL DEFAULT FALSE,
    low_stock_threshold   NUMERIC(20,6) NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INTEGER NOT NULL DEFAULT 0
);
ALTER TABLE reminder_household_rule ADD CONSTRAINT ck_reminder_rule_expiry_days
    CHECK (expiry_disabled OR (
        array_length(expiry_reminder_days,1) IS NOT NULL
        AND expiry_reminder_days <@ ARRAY(SELECT generate_series(1,3650)::SMALLINT)
        AND array_length(expiry_reminder_days,1) =
            (SELECT count(DISTINCT d) FROM unnest(expiry_reminder_days) d)
    ));
ALTER TABLE reminder_household_rule ADD CONSTRAINT ck_reminder_rule_low_stock
    CHECK (low_stock_disabled OR low_stock_threshold > 0);

-- 2) 提醒任务（单表 + kind，未完状态唯一软合并，保留历史 DONE/IGNORED）
CREATE TABLE reminder_task (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL REFERENCES household(id),
    kind                VARCHAR(20) NOT NULL,                       -- EXPIRY | LOW_STOCK
    lot_id              UUID,                                       -- EXPIRY 非空；LOW_STOCK 空
    item_id             UUID NOT NULL,                              -- 两 kind 都冗余存以便排序与筛选
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',         -- OPEN | SNOOZED | DONE | IGNORED
    due_at              TIMESTAMPTZ NOT NULL,                        -- EXPIRY=lot.expiry；LOW_STOCK=评估时刻
    severity            VARCHAR(20) NOT NULL,                        -- INFO | WARN | URGENT
    threshold_snapshot  JSONB,                                       -- 触发时生效临期天数档/低库存阈值快照
    qty_snapshot        NUMERIC(20,6),                               -- LOW_STOCK 触发时当前库存
    snoozed_until       TIMESTAMPTZ,
    last_reconciled_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_reminder_task_kind     CHECK (kind IN ('EXPIRY','LOW_STOCK')),
    CONSTRAINT ck_reminder_task_status   CHECK (status IN ('OPEN','SNOOZED','DONE','IGNORED')),
    CONSTRAINT ck_reminder_task_severity CHECK (severity IN ('INFO','WARN','URGENT')),
    CONSTRAINT ck_reminder_task_lot_xor  CHECK (
        (kind = 'EXPIRY'    AND lot_id IS NOT NULL)
     OR (kind = 'LOW_STOCK' AND lot_id IS NULL)
    ),
    CONSTRAINT fk_reminder_task_lot  FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_reminder_task_item FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id)
);
CREATE UNIQUE INDEX uq_reminder_task_open
    ON reminder_task(household_id, kind, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'))
    WHERE status IN ('OPEN','SNOOZED');
CREATE INDEX idx_reminder_task_household_status_due
    ON reminder_task(household_id, status, due_at);
CREATE INDEX idx_reminder_task_item ON reminder_task(household_id, item_id);

-- 3) 站内通知（任务产生/状态变更/规则变更联动写一条）
CREATE TABLE reminder_notification (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    scope           VARCHAR(20) NOT NULL,          -- TASK_CREATED | TASK_UPDATED | TASK_CLOSED | RULE_CHANGED
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(4000),
    source_task_id  UUID,                           -- reminder_task.id（可空，RULE_CHANGED 无源任务）
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMPTZ,
    CONSTRAINT fk_reminder_notif_task FOREIGN KEY (source_task_id) REFERENCES reminder_task(id)
);
CREATE INDEX idx_reminder_notif_household_unread
    ON reminder_notification(household_id, read, created_at DESC);

-- 4) 事件去重 + dead-letter 重投
CREATE TABLE reminder_processed_event (
    event_id        UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reminder_event_dead_letter (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL,
    payload         JSONB NOT NULL,                  -- StockChangedEvent 序列化
    failure_count   INTEGER NOT NULL DEFAULT 1,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    last_error      VARCHAR(4000),
    last_retry_at   TIMESTAMPTZ,
    abandoned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reminder_dead_letter_event UNIQUE (event_id)
);
```

### 2.1 关键不变量

- `reminder_task` 部分唯一索引 `uq_reminder_task_open` 保证「未完状态同 (kind, lot/item) 至多一行」；DONE/IGNORED 不进唯一索引，可保留历史多行。
- EXPIRY 任务 `lot_id` 必填且指向库存批次（复合外键含 household_id）；LOW_STOCK 任务 `lot_id` 必空、`item_id` 必填。
- 每日扫描重算不会因同一 lot 多次进窗口而重复建任务——`Reconciler` 先 `SELECT ... FOR UPDATE` 取现有未完行，有则 UPDATE，无则 INSERT；并发由部分唯一索引与 `ON CONFLICT DO NOTHING` 兜底，再 `SELECT FOR UPDATE` 处理竞态。
- 家庭默认规则按家庭单例，首次 `requireRule(householdId)` 命中缺失时按 spec 默认值 insert（懒初始化、单事务、唯一约束兜底并发）。

### 2.2 CatalogApi / InventoryApi 扩展（不改表，只追加公开 DTO 字段/方法）

**`CatalogApi.ItemInfo` 追加字段**：
- `String expiryReminderMode`
- `List<Short> expiryReminderDays`
- `String lowStockMode`
- `BigDecimal lowStockThreshold`

`ItemService.toItemResponse` 与 `requireItem`/`requireActiveItem` 返回的 `ItemInfo` 填这些字段；既有字段不回归（向后兼容测试覆盖）。

**`InventoryApi` 追加方法**：
- `List<LotInfo> lotsOfItem(UUID householdId, UUID itemId)` —— 新 `record LotInfo(UUID lotId, UUID itemId, LocalDate expiryDate, BigDecimal totalQuantity)`；实现用一条聚合 SQL（`SUM(inventory_stock_position.quantity)` per lot；`expiry_date` 取自 `inventory_lot`，可为空）。
- `BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId)` —— 复用同聚合的总量。

聚合 SQL 由 `inventory` 模块内部 Mapper 实现，复用阶段四 `ConsistencyCheckMapper` 同款口径（含冲正/移位后正确），实体不外泄。

## 3. 可靠事件投递与任务产生（Reconciler / Listener / 重试）

### 3.1 inventory 发布改造（最小侵入，仅 inventory `event` 包）

- `InventoryEventPublisher.publish(event)` 改为通过 Spring Modulith 的事务内事件登记：在 `@Transactional` 库存命令方法内 `ApplicationEventPublisher.publishEvent(StockChangedEvent)` 即由 Spring Modulith 自动登记到 `event_publication` 系统表，事务提交后由 `OrderedTransactionEventPublisher` 异步派发。
- 配置：新增 `@Configuration(proxyBeanMethods=false)` `InventoryEventConfig`（如需显式 `EventExternalizationConfiguration` 指定 after-commit、complete-after-tx、面向 `StockChangedEvent`）。实施时按 Spring Modulith 2.0.5 实际能力确定最小显式配置；不引入 Kafka/Rabbit。
- 不改 `StockChangedEvent` 已有字段；`eventId` 仍由发布方生成，持久化到 `event_publication` 的 `event_id` 列，自带去重键。
- 改造后原同步路径作为应急回退保留意识：若 Modulith 投递依赖卸任，reminder listener 可临时回退同步 `@EventListener`（不影响库存主链路）。

### 3.2 reminder 消费侧（AFTER_COMMIT + 去重 + dead-letter + 重投）

```
StockChangedEvent 提交后
  → Spring Modulith 派发给 ReminderEventListener.onStockChanged(evt)
  → 在独立小事务内：
      INSERT INTO reminder_processed_event(event_id) VALUES (evt.eventId)
        ON CONFLICT DO NOTHING → 受影响行数 0 → 直接返回（已处理）
      reconcile(affectedLotId=evt.lotId, affectedItemId=evt.itemId)
      提交本事务
  → 抛未捕获异常时：捕获、写一行 reminder_event_dead_letter
        (event_id, payload, next_retry_at=now+退避)
```

`@Scheduled`（`EventRetryService.retryPending`，fixedDelay 建议 30s）扫描 dead-letter 中 `next_retry_at<=now` 且 `abandoned=false` 的行：重放 payload 为 `StockChangedEvent`，再次走 listener 入口；若 `reminder_processed_event` 已命中（说明此前已成功）则跳过并删除该 dead-letter 行；重投成功后删除；超过阈值（10 次）仍失败标记 `abandoned=true`，写一条 `REMINDER_EVENT_POISON` 审计 + 系统通知（属运维告警，非用户任务）。

### 3.3 Reconciler 单一入口（事件与每日扫描共用）

```java
void reconcile(UUID householdId, List<UUID> affectedLotIds,
               List<UUID> affectedItemIds, boolean dailyScan);
```

- **临期路径**：对每个 affected lot —— 取 `lot.expiry_date`、`resolveEffectiveExpiryRule(item, householdRule)`；按生效天数档（如 `[30,7,1]`）计算 `daysLeft = expiryDate - today`；决定是否进入窗口与 severity（`daysLeft<=1`→URGENT、`<=7`→WARN、`<=30`→INFO；超过最大档 → 不在窗口，不建任务）。未完任务存在 → UPDATE due_at/severity/threshold_snapshot（**保留** snoozed_until）；在窗口且无未完 → INSERT OPEN；不在窗口但未完 → 自动关闭为 DONE（`threshold_snapshot` 内记 `{"autoClosed": true, "reason":"LOT_CONSUMED|LOT_RECOVERED|LOT_REVERSAL"}`），写 `TASK_CLOSED` 通知。`daysLeft<=0` 视为 URGENT，不自动关闭——只有消耗/报损/移位清空/冲正才会触发自动关闭。
- **低库存路径**：对每个 affected item —— 取 `currentTotalStockOfItem`、`resolveEffectiveLowStockRule(item, householdRule)`。库存 <= 阈值且无未完任务 → INSERT OPEN（severity 按 `(threshold-qty)/threshold` 比例档位 INFO/WARN/URGENT，qty=0 极端 URGENT）；有未完 → UPDATE snapshot/severity 或保持。库存 > 阈值且未完 OPEN/SNOOZED → 自动关闭为 DONE（`autoClosed=RECOVERED`），写通知。**移位（同 item 同 lot 总库存不变）不产新低库存任务、不误关**（覆盖回归）。
- **每日扫描**：`ExpiryScanScheduler` 列出所有家庭、所有「有 expiry_date 且未完 OPEN/SNOOZED 的 lot」+「今天新进入窗口但无任务的 lot」；调 `reconcile(dailyScan=true)`。同时把 `SNOOZED 且 snoozed_until<=now()` 的任务 UPDATE 回 OPEN（与临期重算同事务）。

### 3.4 生效规则解析

```java
ExpiryRule resolveEffectiveExpiryRule(ItemInfo item, HouseholdRule hh) {
    if (item.expiryReminderMode() == DISABLED || hh.expiryDisabled()) return OFF;
    days = item.expiryReminderMode() == CUSTOM
           ? item.expiryReminderDays()
           : hh.expiryReminderDays();
    return (days != null && !days.isEmpty()) ? new ExpiryRule(days) : OFF;
}
```

低库存同理：`item.lowStockMode()` 与 `hh.lowStockDisabled()`，阈值取 CUSTOM 物品值或家庭默认值；阈值精度校验按物品基础单位 `decimalScale`（阶段三已存）。

### 3.5 通知联动（同一事务内）

reconcile 产生或关闭任务后，在**同事务内**写 `reminder_notification` 一行：

| 事件 | scope | title 示例 |
|---|---|---|
| 新建临期任务 | `TASK_CREATED` | 「{物品名}（批次 …）将在 {N} 天内到期」 |
| 新建低库存任务 | `TASK_CREATED` | 「{物品名} 库存仅剩 {qty}，低于阈值 {阈值}」 |
| 任务自动关闭 | `TASK_CLOSED` | 「{物品名} 临期任务已自动关闭」 |
| 规则修改 | `RULE_CHANGED` | 「家庭默认提醒规则已更新」（系统通知，无源任务） |

由 reconcile 内的小帮助函数生成，避免再开系统事件造成双链。`source_task_id` 可空（RULE_CHANGED 无源任务）。

## 4. 任务状态机与端点契约

### 4.1 状态机

```
                 ┌──────────────────── 自动转换（reconcile） ────────────────────┐
                 │                                                                │
   新建 ─→ OPEN  ←──(snoozed_until<=now，每日扫描)──  SNOOZED                       │
             │  │                                                                 │
             │  └─→(用户 POST /complete)→ DONE                                     │
             │  └─→(用户 POST /ignore) → IGNORED                                   │
             │                                                                    │
        (事件: lot 被消耗/报损/移位清空 / 库存恢复)                                    │
             └→ 自动 CLOSED(=DONE，reason=AUTO_CLOSED)  ← 由 reconcile 完成         │
                                                                                      │
   IGNORED/DONE  ←─(用户 POST /reopen)─ 用户手动重启                                  │
```

- 自动关闭走 `DONE`，并在 `threshold_snapshot`(JSONB) 内记 `{"autoClosed":true, "reason":"LOT_CONSUMED|LOT_RECOVERED|LOT_REVERSAL"}` 区分人工完成；不新增第五态（避免扩 spec 枚举）。
- `IGNORED` 表示「本次风险忽略」——reconcile 时若该 (kind,lot/item) 当前仍在风险窗口内，**不自动重开**；需用户手动 POST `/reopen`。
- `reopen` 复用同一行 UPDATE 回 OPEN + 清 `snoozed_until` + 重新 reconcile 刷新 due_at/severity；不新建行（部分唯一索引内同行 UPDATE）。

### 4.2 Reconciler 对各状态的写入规则

| 当前状态 | reconcile 在风险窗口内 | reconcile 不在风险窗口 |
|---|---|---|
| OPEN | UPDATE due_at/severity/snapshot | UPDATE→DONE(autoClosed) reason=LOT_CONSUMED/RECOVERED/REVERSAL，写 TASK_CLOSED 通知 |
| SNOOZED | UPDATE due_at/severity/snapshot（**保留** snoozed_until） | 同上自动关闭 |
| SNOOZED 且 snoozed_until<=now | UPDATE→OPEN + 刷新（每日扫描路径） | 同上自动关闭 |
| DONE/IGNORED | **不动**（历史，等待用户 reopen） | 不动 |

### 4.3 端点契约（全部受认证、家庭绑定、Problem Details、X-Request-Id；写操作 CSRF）

| 方法 | 路径 | 角色 | 描述 |
|---|---|---|---|
| GET | `/api/v1/reminder/rules` | 全员 | 返回家庭默认规则（无则懒初始化后返回） |
| PUT | `/api/v1/reminder/rules` | OWNER/ADMIN | 更新规则；body 含 `expiryDisabled`、`expiryReminderDays`（CUSTOM 校验 1–3650 互异正整数降序）、`lowStockDisabled`、`lowStockThreshold`（>0，精度不超物品单位）、`version`；乐观锁版本冲突 → `REMINDER_RULE_VERSION_CONFLICT` 409；写 `RULE_CHANGED` 通知 + 审计 REMINDER_RULE_UPDATE |
| GET | `/api/v1/reminder/tasks` | 全员 | 分页；query: `kind`、`status`、`itemId`、`overdue`(bool 仅 due_at<now)、`page`/`pageSize`；默认按 `severity(URGENT>WARN>INFO)` 再按 `due_at ASC` 排序；返回 `items/total/page/pageSize` |
| POST | `/api/v1/reminder/tasks/{id}/snooze` | 全员 | body `until`(ISO)；校验 until>now+1min 且 <= now+3650d；UPDATE→SNOOZED + `snoozed_until`；审计 REMINDER_TASK_SNOOZED |
| POST | `/api/v1/reminder/tasks/{id}/complete` | 全员 | UPDATE→DONE（清 snoozed_until）；审计 REMINDER_TASK_COMPLETED |
| POST | `/api/v1/reminder/tasks/{id}/ignore` | 全员 | UPDATE→IGNORED；审计 REMINDER_TASK_IGNORED |
| POST | `/api/v1/reminder/tasks/{id}/reopen` | 全员 | 仅当 status=IGNORED 或 DONE；UPDATE→OPEN + 重新 reconcile 该单任务刷新 due_at/severity；审计 REMINDER_TASK_REOPENED |
| GET | `/api/v1/reminder/dashboard?days=7&topN=8` | 全员 | 返回聚合（见下） |

### 4.4 dashboard 返回结构

```json
{
  "expiryWithin7Days": {
    "count": 12,
    "items": [{"itemId":"..","lotId":"..","name":"..","daysLeft":3,"expiryDate":"2026-07-29"}, ...]
  },
  "lowStockItems": {
    "count": 5,
    "items": [{"itemId":"..","name":"..","qty":"0.5","threshold":"2"}, ...]
  },
  "priorityTasks": {
    "count": 23,
    "items": [{"taskId":"..","kind":"EXPIRY","severity":"URGENT","title":"..","dueAt":"..","itemId":"..","lotId":".."}, ...]
  },
  "generatedAt": "2026-07-26T03:00:00Z"
}
```

`expiryWithin7Days`、`lowStockItems`、`priorityTasks` 各返回前 N 条与 total，满足节 1「提醒模块出聚合、前端二次拼装」（5b 再拼装待盘点与最近流水）。

### 4.5 通知端点

| 方法 | 路径 | 角色 | 描述 |
|---|---|---|---|
| GET | `/api/v1/notifications?page=1&pageSize=20&unreadOnly=false` | 全员 | 分页通知（按 created_at DESC），过滤当前家庭 |
| GET | `/api/v1/notifications/unread-count` | 全员 | 返回 `{ "count": N }`（5b 顶栏轮询）|
| POST | `/api/v1/notifications/{id}/read` | 全员 | 标记单条已读 |
| POST | `/api/v1/notifications/read-all` | 全员 | 标记家庭所有未读为已读 |

### 4.6 错误码新增（`ReminderExceptionHandler`，复用 Catalog 模式）

| errorCode | HTTP | 触发 |
|---|---|---|
| `REMINDER_RULE_NOT_INITIALIZED` | 500 | 懒初始化异常兜底（正常不触发）|
| `REMINDER_RULE_VERSION_CONFLICT` | 409 | PUT 规则版本不匹配 |
| `REMINDER_RULE_EXPIRY_DAYS_INVALID` | 422 | 非降序 1–3650 互异正整数 |
| `REMINDER_RULE_LOW_STOCK_INVALID` | 422 | 阈值 <=0 或精度超单位 |
| `REMINDER_TASK_NOT_FOUND` | 404 | 状态机操作 id 跨家庭或不存在（不泄露存在性）|
| `REMINDER_TASK_INVALID_TRANSITION` | 409 | reopen 非 DONE/IGNORED、snooze 非 OPEN/SNOOZED |
| `REMINDER_TASK_SNOOZE_UNTIL_INVALID` | 422 | until 越界 |

`REMINDER_EVENT_POISON` 仅审计/系统通知，非对外错误码。

审计动作白名单新增：`REMINDER_RULE_UPDATE`、`REMINDER_TASK_SNOOZED`、`REMINDER_TASK_COMPLETED`、`REMINDER_TASK_IGNORED`、`REMINDER_TASK_REOPENED`（SystemApi 的 `action` 无后端枚举校验，前端常量同步）。

## 5. 测试策略与验收门槛

沿用阶段四套路：单元 + Testcontainers 集成 + Modularity + 契约基线。所有集成测试 truncate 列表追加 `reminder_task`、`reminder_household_rule`、`reminder_notification`、`reminder_processed_event`、`reminder_event_dead_letter`。时间相关测试注入 reminder 模块 `Clock` bean（`Clock.fixed(...)`）。

### 5.1 单元测试

- `ReminderRuleResolverTest` —— 生效规则解析（INHERIT/CUSTOM/DISABLED × 家庭默认/物品级、家庭关闭、物品关闭），9 种组合。
- `ExpirySeverityClassifierTest` —— `daysLeft` → severity（含 `daysLeft<=0` 仍 URGENT 不自动关）。
- `LowStockSeverityClassifierTest` —— `(qty, threshold)` → severity（含 qty=0 极端 URGENT）。

### 5.2 Testcontainers 集成测试

1. `ReminderHouseholdRuleIntegrationTest` — 首次 GET 懒初始化 30/7/1、低库存 1；PUT 版本冲突 `REMINDER_RULE_VERSION_CONFLICT`；PUT 非降序/重复/超 1–3650 `REMINDER_RULE_EXPIRY_DAYS_INVALID`；低库存 ≤0 `REMINDER_RULE_LOW_STOCK_INVALID`；PUT 写 `RULE_CHANGED` 通知 + 审计条目。
2. `ReminderReconcilerIntegrationTest`（核心）— 入库新批次到期日在 30 天内 → reconcile 产生 EXPIRY OPEN + 按档 severity + `TASK_CREATED` 通知；365 天后不产；物品 DISABLED 与 hh.expiryDisabled 均不产；领用清空 lot → 自动关 DONE(autoClosed=LOT_CONSUMED) + `TASK_CLOSED` 通知；入库使 item 总库存从 < 阈值变 > 阈值 → 自动关 LOW_STOCK(autoClosed=RECOVERED)；领用跌破阈值 → 产 LOW_STOCK OPEN；**移位总库存不变不产不误关**；冲正 INBOUND → reconcile 处理 lot 回 0 自动关；已有 SNOOZED 仍风险 → 只刷新保留 snoozed_until；SNOOZED snoozed_until<=now → 每日扫描转回 OPEN；IGNORED/DONE 行 reconcile 不动且无唯一冲突。
3. `ReminderEventListenerIntegrationTest`（可靠投递）— 正常事件 → `reminder_processed_event` 命中一行 + reconcile 完成；同 `eventId` 投两次 → 第二次 ON CONFLICT 跳过、计数不变；强制 listener 抛异常 → 写 dead_letter；调度重投成功 → dead_letter 删除、效果等于一次；超 10 次失败 → `abandoned=true` + `REMINDER_EVENT_POISON` 审计 + 系统通知。
4. `ExpiryScanSchedulerIntegrationTest` — Clock 模拟「今天 2026-12-01、lot 到期 2026-12-29」未在窗口；改 Clock 到 2026-12-25 → 每日扫描新建临期 OPEN(daysLeft=4, WARN)；改 Clock 到 2026-12-29 → URGENT；SNOOZED 过期转回 OPEN；跨家庭不串扰。
5. `ReminderTaskStateIntegrationTest` — snooze/complete/ignore/reopen 各转换、非法 `REMINDER_TASK_INVALID_TRANSITION` 409；reopen 无新行（同行 UPDATE）；各动作写审计；snooze until 越界 `REMINDER_TASK_SNOOZE_UNTIL_INVALID`。
6. `ReminderDashboardIntegrationTest` — 12 lot 7 天内到期、5 个低库存物品、3 个 URGENT priority task → dashboard count 与 topN 正确；`days=7/topN=8` 入参生效。
7. `NotificationIntegrationTest` — GET 分页 + unread 过滤；unread-count；read-one；read-all 后 unread-count=0。
8. `ReminderEndpointIntegrationTest` — MockMvc 全端点 happy path + 权限：MEMBER 调 PUT rules → 403（HouseholdApi.hasAtLeastRole(ADMIN) 校验）；GET 跨家庭任务 id → `REMINDER_TASK_NOT_FOUND` 404；CSRF + Problem Details 形态与既有一致。
9. `CatalogApiReminderFieldsIntegrationTest` — `requireItem`/`requireActiveItem` 返回 `ItemInfo` 含 4 个 reminder 字段且值正确；既有字段不回归。
10. `InventoryApiLotsOfItemIntegrationTest` — `lotsOfItem` 聚合 SQL 与 `currentTotalStockOfItem` 在多位置、冲正后、一致性检查对账后返回值正确。

### 5.3 架构与契约

- `ModularityTests` 新增 `reminderModuleExistsAndDependenciesAreValid()`（断言 reminder 模块存在且 `verify()` 通过）。
- `OpenApiContractTest` 基线升至 V2（含 `/api/v1/reminder/**`、`/api/v1/notifications/**`）。

### 5.4 验收门槛（5a 单段，对应 roadmap「变更库存或到期日恰好创建、更新和关闭预期任务一次，包括模拟事件处理器失败和重试之后」）

1. `make backend-test` 全绿（含上述 Testcontainers 用例全覆盖）。
2. `cd backend && ./mvnw -q -Dtest=ModularityTests test` 通过。
3. 事件改造无回归：`InventoryEventPublisher` 改造后阶段四库存集成测试（`StockCommandServiceIntegrationTest`/`ReversalServiceIntegrationTest` 等）仍全绿。
4. 事件失败重试场景实物通过：dead_letter → 重投 → 成功链路在 `ReminderEventListenerIntegrationTest` 被验证。
5. 全空库 `make backend-build` 构建成功（V2 在 Testcontainers 空库由 Flyway 执行成功）。
6. 不提交任何前端代码（前端在 5b）。

## 6. 实施拆分、风险与回退、文档

### 6.1 5a 内部任务拆分（供 writing-plans 写出可执行计划，每任务一次提交，中文 body + 英文前缀）

1. V2 迁移（reminder 五张表 + 约束 + 索引）—— 空库 Flyway 验证。
2. reminder 模块骨架（package-info、ReminderApi 公开只读端口、ReminderExceptionHandler、七类异常、ClockConfig）。
3. CatalogApi/InventoryApi 扩展（ItemInfo +4 字段、InventoryApi +lotsOfItem/currentTotalStockOfItem + LotInfo record）；ItemService/InventoryService 填充；向后兼容回归测试。
4. 持久化（Entity/Mapper/XML：HouseholdRule、Task、Notification、ProcessedEvent、DeadLetter）。
5. ReminderRuleService（懒初始化 + 读写 + 乐观锁 + 校验 + RULE_CHANGED 通知 + 审计）—— TDD。
6. ReminderRuleResolver + severity 分类器单元测试（纯函数 TDD）。
7. ReminderReconciler（临期/低库存双路径、未完合并、自动关闭、通知联动）—— TDD by Testcontainers。
8. InventoryEventPublisher 改造为 Spring Modulith 可靠投递（含 EventExternalizer 配置如需）—— 跑回归证明无破坏。
9. ReminderEventListener + 去重表写入 + dead-letter + EventRetryService + @Scheduled 重投 —— TDD（正常/重复/异常/超次）。
10. ExpiryScanScheduler（每日扫描 + SNOOZED 过期转 OPEN）—— TDD（Clock 覆盖）。
11. ReminderTaskStateService（snooze/complete/ignore/reopen + 记审计）—— TDD。
12. DashboardService + 聚合查询 Mapper —— TDD。
13. NotificationService + 端点 —— TDD。
14. ReminderController + 全端点（rules/tasks/dashboard/notifications，MockMvc 集成测试 + 权限）。
15. ModularityTests 扩充 reminder 断言 + OpenApiContractTest 基线升至 V2。
16. 5a 收尾：`make backend-test` 全绿、`make backend-build` 成功；写收尾记录。

### 6.2 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Spring Modulith 2.0.5 可靠投递默认行为与预期不符（AFTER_COMMIT 是否需显式 `EventExternalizationConfiguration`） | 事件改造退化为同步或不可重试 | 任务 8 先写最小集成测试验证 `event_publication` 表有登记、提交后派发；若默认未满足，显式配置 `EventExternalizationConfiguration` 指定 after-commit；不引入 Kafka/Rabbit。 |
| `lotsOfItem` 聚合 SQL 未考虑冲正/移位/多位置出错 | 临期/低库存误判 | 复用 ConsistencyCheckMapper 同款聚合逻辑（阶段四已验证）；用 10 个 Testcontainers 用例覆盖。 |
| 部分唯一索引 `WHERE status IN (OPEN,SNOOZED)` 与 `ON CONFLICT` 语法在 PostgreSQL 17 行为不符 | 并发 merge 仍冲突 | 用 `ON CONFLICT DO NOTHING` 后 `SELECT ... FOR UPDATE` 再 UPDATE；测试并发两线程 reconcile 同一 lot 仅产一行。 |
| 每日扫描与事件在同一 lot 撞 reconcile | 双重处理 | 共用 `reminder_processed_event` 去重不适用（无 eventId 扫描）；改用「reconcile 前先 `SELECT FOR UPDATE` 现有未完行」串行化同一 lot；扫描 scope 加 `reconcileToken` 防重复生成。 |
| 把同步发布改可靠投递后阶段四库存集成测试回归 | 4a 不可逆破坏 | 任务 8 后立即跑 `StockCommandServiceIntegrationTest` 全套；若 Modulith 投递依赖卸任，保留同步路径作为回退（不破坏既有行为，只在 reminder 侧加 AFTER_COMMIT）。 |
| 家庭时区与 `@Scheduled` 默认 JVM 时区 | 多家庭单部署只有一时区 | JVM 默认（`ZIJA_TZ` 或容器时区），首期单家庭无影响；实施时 `@Scheduled(cron=.., zone="${zija.schedule.zone:Asia/Shanghai}")` 可配置；spec §13「家庭设置决定显示时区」属显示，cron 用部署时区即可（5a 文档说明）。 |

### 6.3 回退策略（满足「可回退」门槛）

- V2 是新表，不改任何既有表数据；`git revert` 本次提交即回到 5a 前状态。
- CatalogApi/InventoryApi 扩展是 Java 字段/方法追加，移除扩展即回退；既有调用者无感知。
- InventoryEventPublisher 改造若引起回归，可临时回滚为同步发布（reminder 侧 listener 改为同步 `@EventListener` 作为应急路径），不影响库存主链路。

### 6.4 文档（5a 完成时交付）

- 本 spec：`docs/superpowers/specs/2026-07-26-phase5a-reminder-backend-design.md`。
- 完成记录：`docs/superpowers/notes/2026-07-26-phase5a-reminder-backend-completion.md`（最终验证命令、提交 ID、测试统计）。
- 5b 实施前独立写 5b spec + plan，5c 同理，不在 5a spec 中规划。
- 不更新 `docs/agents`、`docs/design/redesign-visual-spec.md`（视觉相关在 5b）。

### 6.5 5a 完成定义

- 全部 16 个任务提交完成、工作树干净。
- `make backend-test`、`make backend-build` 通过。
- roadmap 阶段五验收门槛中与后端可靠相关的部分（「库存变化恰好创建/更新/关闭预期任务一次、含事件失败与重试之后」）由集成测试证据支撑。
- 5b/5c 未完成不阻塞 5a 验收（与 4a/4b 同样的切口原则）。

## 7. 已确认关键决策（来自设计对话）

1. 阶段五拆分为 5a 后端 / 5b 前端 / 5c 可选 SMTP，每段独立 spec→plan→实施；5c 列为后续可选，先做 5a+5b。
2. 提醒任务持久化用单表 + `kind`（EXPIRY/LOW_STOCK），临期绑定 lot、低库存绑定 item，统一状态机/排序/审计/去重。
3. 触发与可靠性采用 Spring Modulith 事务内登记 + AFTER_COMMIT 派发 + `reminder_processed_event` 去重表 + dead-letter + `@Scheduled` 重投；失败可重试且不重复处理。
4. 时间驱动采用每日定时 + 事件双驱动，复用同一 `Reconciler` 与去重机制；每日扫描同时刷新 SNOOZED 过期回 OPEN。
5. 家庭默认提醒规则新建 `reminder_household_rule` 单例表（不污染 household 表，乐观锁独立审计）。
6. 跨模块读取提醒配置与批次到期日通过扩展现有 `CatalogApi.ItemInfo` 与 `InventoryApi`（仅追加字段/方法，不改既有契约语义）。
7. 首页聚合由提醒模块出 `GET /api/v1/reminder/dashboard`，待盘点数与最近流水由前端复用 inventory 现有分页端点二次拼装（不造跨模块耦合端点）。
8. 时间来源在 reminder 模块内定义 `Clock` bean，测试可注入；不强制改全后端现有时间调用。
9. 任务唯一性采用 `(household, kind, COALESCE(lot_id, fixed))` 部分唯一索引限未完状态；DONE/IGNORED 保留历史不被重开（除非用户手动 reopen）。
10. 站内通知后端含在 5a（事件→任务→通知闭环）；5b 只做通知 UI 与未读角标。
11. 自动关闭走 `DONE` + `threshold_snapshot` JSONB 记 `autoClosed/reason` 区分人工完成，不扩 spec 四态枚举。
12. `IGNORED` 不被 reconcile 自动重开，需用户 `reopen`；`reopen` 同行 UPDATE 不新建。
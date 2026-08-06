# ADR-016: 提醒任务模型与事件可靠性

## 状态

已批准

## 背景

库存变化需要自动产生、更新和关闭提醒任务（临期、低库存）。spec §6.6 要求任务状态机、家庭默认规则与物品级覆盖。事件投递可能失败，必须有重试和死信机制。如何在模块边界内实现可靠的提醒闭环？

## 决策

### 1. 提醒任务模型

- 单表 + `kind`（`EXPIRY` / `LOW_STOCK`）：临期任务绑定 `lot_id`，低库存任务绑定 `item_id`，统一状态机、排序、审计和去重。
- 任务状态：`OPEN` → `SNOOZED` / `DONE` / `IGNORED`。
- 任务唯一性：`(household, kind, COALESCE(lot_id, fixed))` 部分唯一索引限定未完状态；`DONE` / `IGNORED` 保留历史不被自动重开（除非用户手动 `reopen`）。
- `IGNORED` 不被 reconcile 自动重开，需用户 `reopen`（同行 UPDATE，不新建）。
- 自动关闭走 `DONE` + `threshold_snapshot` JSONB 记录 `autoClosed/reason` 区分人工完成。

### 2. 快照策略

- 任务表存 `qty_snapshot` 和 `threshold_snapshot`（数值快照），不存物品名。
- 物品名通过 `CatalogApi.itemNames`（返回 `Map<UUID, String>`）读时实时补齐——改名即时可见，无滞后。
- 数值快照虽在 reconcile 时刻钉死，但每次库存变更事件都会触发重算，库存未变即快照仍然成立，故等价于实时。
- 物品名不进任务快照：名字是活数据，快照存历史不存展示字段（见 ADR-010）。

### 3. 双驱动 Reconcile

- **每日定时扫描**：`@Scheduled` crontab 按部署时区 ~03:00 执行，扫描全部活跃物品，创建/更新/关闭任务，同时刷新 `SNOOZED` 过期回 `OPEN`。
- **事件驱动**：库存变更事件触发增量 reconcile，只处理受影响的 `lotId` / `itemId`。
- 两条路径共用同一 `Reconciler.reconcile(householdId, affectedLotIds, affectedItemIds, dailyScan)` 入口，去重机制一致。

### 4. 事件可靠性

- `StockChangedEvent` 在 `inventory` 事务内通过 Spring Modulith 事件登记，事务提交后异步派发。
- `reminder` 模块订阅事件后，先写 `reminder_processed_event` 去重表（`INSERT ... ON CONFLICT DO NOTHING`），在独立事务（`REQUIRES_NEW`）内处理。
- 处理失败写 `reminder_event_dead_letter` 并删除刚插入的 `processed_event`。
- `EventRetryService` `@Scheduled` 定时轮询 dead-letter（fixedDelay 30s），指数退避重试。
- 超过 10 次失败标记 `abandoned=true`，写 `REMINDER_EVENT_POISON` 审计 + 系统通知（属运维告警，非用户任务）。

### 5. 规则变更全量重算

- `updateRule` 写入成功且审计成功后，发布内部事件 `ReminderRuleChangedEvent`。
- `@TransactionalEventListener(AFTER_COMMIT)` 监听器在事务提交后用 `REQUIRES_NEW` 事务对全部活跃物品全量重算。
- 重算失败只记日志、不破坏已提交的规则，每日 03:00 全量扫描兜底。
- 批次落出提醒窗口时，reconcile 同步关闭既有 OPEN 临期任务（原因 `OUT_OF_WINDOW`），避免残留过期任务。

### 6. 家庭默认规则

- 新建 `reminder_household_rule` 单例表，不污染 `household` 表。
- 乐观锁独立审计。Owner/Admin 可写，Member 只读。
- 物品可继承默认值、覆盖提醒天数或关闭临期提醒。
- `CUSTOM` 临期提醒日必须是 1–3650 的互异正整数，按从大到小保存。
- `CUSTOM` 低库存阈值必须大于零，精度不得超过关联单位的 `decimal_scale`。

### 7. 首页聚合

- `GET /api/v1/reminder/dashboard` 由提醒模块提供，返回风险卡片和优先处理任务。
- 待盘点数和最近流水由前端复用 `inventory` 现有分页端点二次拼装，不造跨模块耦合端点。

### 8. SMTP 邮件（可选）

- 不配置 SMTP 时 `MailService` 短路，主业务不依赖邮件成功。
- 紧急邮件由 reconcile 触发，摘要邮件由定时任务发送。
- 邮件失败不重试紧急、不阻塞主流程，只记审计/系统通知。
- 模板固定中文 HTML，不允许用户自定义。

## 考过的备选

- **双表分离（临期 + 低库存）**：状态机、排序、去重逻辑重复。单表 + `kind` 更简洁。
- **任务存物品名快照**：改名后首页显示旧名直到每日扫描，家庭成员会感觉系统不准。读时查询一次即可做到即时一致（见 ADR-010）。
- **回放 `event_publication` 表做重建**：完成事件可能被清理，且不含 reminder listener 的历史绑定。事件用于增量、快照拉取用于重建（见 ADR-005）。
- **Kafka/Rabbit 消息中间件**：spec §3.3 明确不做。Spring Modulith 事务内事件登记 + 本地 dead-letter + 定时重试在单家庭规模下足够。
- **规则变更后不重算**：库存未变但阈值变了会导致滞后窗口。全量重算封堵此窗口。

## 后果

- 库存变化可以自动产生、更新和关闭相应任务，提醒闭环可靠。
- 事件投递失败不丢失：dead-letter + 定时重试 + 放弃告警。
- 规则变更立即生效，无滞后窗口。
- 代价是 dead-letter 表和 `processed_event` 表需运维关注，但单家庭规模下流量极低。

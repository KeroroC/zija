# ADR-006: 扩展 StockChangedEvent 以携带操作人与审计字段

## 状态

已批准

## 背景

阶段六报表与导出需要按成员筛选流水、按操作人展示，但 `StockChangedEvent`（阶段四发布、阶段五消费）未携带 `operatorAccountId`、`reason`、`reversalOf` 等审计字段；`reporting_movement_flat` 投影必须存这些字段才能满足 spec §6.8「按成员、操作类型和物品筛选的流水」报表与导出。

`StockChangedEvent` 是 record 公共类型，已存在两个跨模块消费者：`ReminderEventListener`（5a 已交付）与即将新增的 reporting listener。reminder 还把事件序列化为 `reminder_event_dead_letter.payload` JSONB 由 `EventRetryService.fromMap` 用位置构造器反序列化重放。

## 决策

1. 在 `StockChangedEvent` record 上**追加**字段 `operatorAccountId`、`reason`、`reversalOf`（`UUID`，可空），不删除或重命名已有字段。
2. `StockCommandService` / `ReversalService` / `StocktakeService` 在所有发布点填充新字段（来自 `MovementEntity` 已有列）。
3. 同步更新 `ReminderEventListener.toMap` 与 `EventRetryService.fromMap`：
   - `toMap` 增加新键，旧键保持原顺序。
   - `fromMap` 对缺键（旧 dead-letter）容错取 `null`。
4. `reporting_movement_flat` 投影存 `operator_account_id`，但不存 `display_name`；报表/导出展示操作人名时由 reporting 调用 `identity`/`household` 的公开只读端口补名，避免事件携带展示字段。

事件契约追加字段视为可接受的非破坏性变更；记录本 ADR 仅为提醒未来读者：公共事件的字段只能追加，不可重排或删除，dead-letter 反序列化器必须健壮处理缺键。

## 考虑过的备选

- **不改 StockChangedEvent，reporting 用 movementId 回查 `InventoryApi.findMovement`**：每事件一次同步调用，事件处理开销大、增加跨模块调用、与「事件管增量」边界重叠。不取。
- **新增 `StockChangedForReportingEvent` 子事件**：双事件机制复杂，发布点需双发，与 Modulith 单事件语义相悖。不取。

## 后果

- 公共事件字段只追加、不重排、不删除成为跨模块合约约束。
- reminder 的 dead-letter 反序列化器必须处理旧 payload 缺键，迁移期完成前不得清理历史 dead-letter。
- `reporting_movement_flat` 含操作人 ID 但展示名靠 identity 拉取端口；UI 列表渲染可能多一次轻量 join 端口调用，性能影响在可接受范围。
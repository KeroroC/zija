# 阶段五 5b：提醒与任务首页 前端 设计方案

- **日期：** 2026-07-26
- **状态：** 已确认，作为 5b 实施计划与验收依据
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` §6.7（任务与风险首页方向）、§7（桌面端信息架构与交互）、§7.3（桌面适配与无障碍）、视觉规范 `docs/design/redesign-visual-spec.md`（松间账册 Pine Ledger）
- **交付路线：** 阶段 5（提醒与任务首页），本段为 5b 前端。5b 依赖 5a 后端契约（`/api/v1/reminder/**`、`/api/v1/notifications/**`）。
- **前置依赖：** 5a 已交付（reminder 后端、端点、通知、dashboard 聚合）。`StockChangedEvent` 可靠投递、任务 reconcile、每日扫描在 5a 已完成后端逻辑。

## 1. 范围与边界

### 1.1 5b 在范围内

- **任务首页**：替换当前 `/` 路由（现为 `SystemStatusView`），改为「任务与风险」首页，呈现：
  - 7 天内到期数量（卡片数 + top N 列表，点进到提醒中心筛选 7 天内到期）
  - 低库存物品数量（卡片数 + top N 列表）
  - 待盘点数量（卡片数，链接到 `/inventory` 盘点页签 `status=DRAFT`；前端调 `GET /api/v1/inventory/stocktakes?status=DRAFT&pageSize=1` 读 `total`）
  - 优先处理任务（按 severity/dueAt 排序，前 N 条，可 snooze/complete/ignore）
  - 快速操作（快速入库、领用、盘点、移位入口，复用既有分步对话框）
  - 最近库存流水（前 10 条，调 `GET /api/v1/inventory/movements?pageSize=10`）
- **提醒中心页** `/reminders`：任务列表分页+筛选（kind/status/itemId/overdue）+ 按紧急度排序；行内操作 snooze/complete/ignore/reopen（抽屉或下拉）。
- **站内通知 UI**：顶栏新增通知铃铛 + 未读角标（轮询 `GET /api/v1/notifications/unread-count`，30s 间隔）；下拉列表显示最近通知；「查看全部」进通知全列表页 `/notifications`；标记单条已读 / 全部已读。
- **家庭默认规则配置 UI**：在「家庭设置」下新增子页 `/settings/reminder`（owner/admin 可写、member 只读）；表单含 expiryDisabled/expiryReminderDays（CUSTOM 多值降序输入，复用物品级同款 `el-select multiple allow-create` 套路）/lowStockDisabled/lowStockThreshold + 版本号；保存 PUT `/api/v1/reminder/rules`。
- **侧边栏启用** `提醒中心`（`/reminders`）；`报表与导出` 仍 disabled（阶段六）；系统状态页迁到 `/system`，侧边栏「家庭」组下加入口。
- 沿用「松间账册」视觉体系（`tokens.css`、`index.css`，禁止组件内硬编码色值）；表格数字列 `tabular-nums`；状态点 `zj-dot-pine/warn/danger`；徽章 `zj-badge-pine/ink`；状态同时用文字+图标表达（无障碍）。

### 1.2 5b 明确不做

- SMTP 邮件 UI / 邮件配置（5c）。
- 任务 reconcile 逻辑、事件投递、每日扫描（5a 已做，前端只消费）。
- 报表、CSV、全局搜索（阶段六）。
- 移动端适配、手机浏览器（spec §7.3 首期不承诺）。
- SSE/WebSocket 实时推送（首期用轮询）。
- Pinia 长期缓存服务端业务数据（仅 session/UI 偏好，与既有约定一致）。

## 2. 信息架构与路由

### 2.1 路由变更

| 路径 | 现状 | 5b 变更 |
|---|---|---|
| `/` | `SystemStatusView` | 改为 `HomeView`（任务与风险首页） |
| `/system` | 不存在 | 新增，承载原 `SystemStatusView`（系统状态） |
| `/reminders` | 占位 disabled | 启用，`RemindersView`（提醒中心） |
| `/notifications` | 不存在 | 新增，`NotificationsView`（通知全列表） |
| `/settings/reminder` | 不存在 | 新增，`ReminderRulesSettingsView`（家庭默认规则，owner/admin） |
| 既有 `/items` `/inventory` `/locations` `/members` `/audit-logs` `/profile` `/settings/catalog` | 不变 | — |

### 2.2 侧边栏（`AppShell.vue`）

- 「物品」组：首页、物品资料、库存管理、位置管理、**提醒中心（启用）**
- 「家庭」组：成员管理、审计日志、个人资料、**系统状态**、家庭设置（owner/admin 子菜单追加「提醒规则」）
- 顶栏新增（与侧边栏并列的顶部右侧）：通知铃铛 + 未读角标 + 登出（既有）

## 3. API 客户端模块

新增 `frontend/src/api/reminder.ts` 与 `frontend/src/api/notification.ts`，沿用 `http.ts` 的 `getJson/postJson/putJson` 助手。

```ts
// reminder.ts
export interface ReminderRule { expiryDisabled: boolean; expiryReminderDays: number[];
  lowStockDisabled: boolean; lowStockThreshold: string; version: number }
export interface ReminderTask { id: string; kind: "EXPIRY"|"LOW_STOCK"; lotId: string|null;
  itemId: string; status: "OPEN"|"SNOOZED"|"DONE"|"IGNORED";
  dueAt: string; severity: "INFO"|"WARN"|"URGENT"; snoozedUntil: string|null }
export interface DashboardGroup<T> { count: number; items: T[] }
export interface DashboardViewItem { taskId: string; kind: string; severity: string;
  title: string; dueAt: string; itemId: string; lotId: string|null }
export interface Dashboard { expiryWithin7Days: DashboardGroup<DashboardViewItem>;
  lowStockItems: DashboardGroup<DashboardViewItem>; priorityTasks: DashboardGroup<DashboardViewItem>;
  generatedAt: string }

export const fetchRules = () => getJson<ReminderRule>("/api/v1/reminder/rules")
export const updateRules = (body: ...) => putJson<ReminderRule>("/api/v1/reminder/rules", body)
export const fetchTasks = (params: URLSearchParams) => getJson<{items:ReminderTask[];total:number;page:number;pageSize:number}>(`/api/v1/reminder/tasks?${params}`)
export const snoozeTask = (id: string, until: string) => postJson<void>(`/api/v1/reminder/tasks/${id}/snooze`, { until })
export const completeTask = (id: string) => postJson<void>(`/api/v1/reminder/tasks/${id}/complete`, {})
export const ignoreTask = (id: string) => postJson<void>(`/api/v1/reminder/tasks/${id}/ignore`, {})
export const reopenTask = (id: string) => postJson<void>(`/api/v1/reminder/tasks/${id}/reopen`, {})
export const fetchDashboard = (days=7, topN=8) => getJson<Dashboard>(`/api/v1/reminder/dashboard?days=${days}&topN=${topN}`)
```

```ts
// notification.ts
export interface NotificationItem { id: string; scope: string; title: string; message: string|null;
  sourceTaskId: string|null; read: boolean; createdAt: string }
export const fetchNotifications = (page=1, pageSize=20, unreadOnly=false) =>
  getJson<{items:NotificationItem[];total:number;page:number;pageSize:number}>(`/api/v1/notifications?page=${page}&pageSize=${pageSize}&unreadOnly=${unreadOnly}`)
export const fetchUnreadCount = () => getJson<{count:number}>(`/api/v1/notifications/unread-count`)
export const markNotificationRead = (id: string) => postJson<void>(`/api/v1/notifications/${id}/read`, {})
export const markAllNotificationsRead = () => postJson<void>(`/api/v1/notifications/read-all`, {})
```

`postJson<void>(..., {})` 与既有约定一致；`void` 泛型表明忽略响应体。

## 4. 页面与组件设计

### 4.1 `HomeView.vue`（`/`，任务与风险首页）

布局遵循 `.page-container`（`max-width: 1120px`）+ `.page-header`（衬线 22px「首页」+ 13px 副标题「任务与风险」）。

区段自上而下：
1. **风险卡片行**（三张并排，`el-row :gutter="24"`，每张 `.page-card` 内：
   - 7 天内到期：数字（`tabular-nums` 28px pine）+「查看清单」链接（`/reminders?kind=EXPIRY&overdue=false`，前端实现 7 天内筛选：调 `fetchTasks` 后按 dueAt<=now+7d 前端筛选，或后端无该筛选时取 status=OPEN 全量过滤）
   - 低库存物品：数字 + 链接 `/reminders?kind=LOW_STOCK`
   - 待盘点：数字（调 `stocktakes?status=DRAFT&pageSize=1` 读 total）+ 链接 `/inventory?tab=stocktakes&status=DRAFT`
2. **优先处理任务**（卡片，`Dashboard.priorityTasks.items` 前 8 条列表，每行：`zj-dot` 按 severity（URGENT→`zj-dot-danger`、WARN→`zj-dot-warn`、INFO→`zj-dot-pine`）+ 标题 + 到期时间 + 操作下拉（snooze/complete/ignore）；行 hover `--zj-pine-50` 可点击进提醒中心）
3. **快速操作**（一行四个按钮：快速入库、快速领用、快速盘点、快速移位；复用 `InboundDialog/ConsumeDialog/StocktakeDialog/TransferDialog`，按现状触发）
4. **最近流水**（调 `GET /api/v1/inventory/movements?pageSize=10`；表格列：时间、物品、类型（中文标签）、数量、操作人；数字列 `tabular-nums`）

数据加载：`onMounted` 并发 `Promise.all([fetchDashboard(7,8), fetchStocktakeDraftCount(), fetchRecentMovements()])`；失败用 Problem Details 错误提示；不在 Pinia 缓存。

### 4.2 `RemindersView.vue`（`/reminders`，提醒中心）

`page-container` + 筛选栏（`--zj-surface-sunken` 底）：kind（全部/临期/低库存）、status（全部/OPEN/SNOOZED/DONE/IGNORED）、overdue（仅过期）、物品名搜索（前端可选；后端无名称搜索，阶段六再做）。

表格（Element Plus `el-table`，行高 ≥52px，`tabular-nums` 数字列）：类型（`zj-badge-pine`/`ink`）、物品、批次/位置（临期显示 lotId 短码，低库存显示 —）、状态（`zj-dot` + 文字）、紧急度（`zj-dot` 颜色）、到期/评估时刻、操作（下拉：snooze/complete/ignore/reopen，按当前 status 决定可见项）。

snooze 弹出 `el-date-picker`（未来时间，限制 ≥ now+1min、≤ now+3650d）→ `snoozeTask(id, ISO)`。

行点击进抽屉 `ReminderTaskDetailDrawer.vue` 显示完整 threshold_snapshot/qty_snapshot/审计信息（只读）。

### 4.3 `NotificationsView.vue`（`/notifications`，通知全列表）

`page-container-narrow`（`max-width: 640px`）或窄列表；分页；每条卡片：scope 徽章（`TASK_CREATED/TASK_CLOSED/RULE_CHANGED`）+ 标题 + 消息 + 时间 + 已读状态；「标记已读」「全部已读」操作。

### 4.4 顶栏通知铃铛（`AppShell.vue` 顶栏右区新增）

`el-badge :value="unreadCount" :hidden="unreadCount===0"` 包裹 `Bell` 图标按钮；点击 `el-popover` 显示最近 5 条未读（调 `fetchNotifications(1,5,true)`）；底部「查看全部」→ `/notifications`；「全部已读」按钮。30s 轮询 `fetchUnreadCount`（`setInterval`，`onUnmounted` 清理；`prefers-reduced-motion` 不影响轮询）。

### 4.5 `ReminderRulesSettingsView.vue`（`/settings/reminder`，家庭默认规则）

`page-container-narrow`（`max-width: 440px`）；表单：
- 临期提醒开关（`el-switch`，关闭则禁用天数输入并 PUT `expiryDisabled=true`）
- 临期天数（复用 `ItemFormDrawer.vue` 同款 `el-select multiple allow-create`，降序 1–3650 校验；仅开关开时显示）
- 低库存开关 + 阈值 `el-input-number`（最小步进按单位 decimalScale；首期只用单一数值，物品单位在家庭默认层不绑定具体单位，按字面数值存）

保存按钮（owner/admin 可见；member 隐藏）；保存调 `updateRules`，乐观锁失败（`REMINDER_RULE_VERSION_CONFLICT`）→ 提示并 `fetchRules` 刷新版本号后让用户重试。规则变化后主动触发首页/提醒中心数据刷新（事件总线或下次进入刷新，首期不引入全局事件）。

## 5. 状态与交互细节

- **任务状态可见操作**（前端隐藏，非安全边界；后端再次校验）：
  - OPEN：snooze、complete、ignore
  - SNOOZED：snooze（改时间）、complete、ignore；显示 `snoozedUntil`
  - DONE：reopen
  - IGNORED：reopen
- **snooze 时间选择**：`el-date-picker type="datetime"`，未来时刻；提交 ISO `OffsetDateTime`（前端用 `dayjs` 或原生 `toISOString()` 带 Z，后端 `OffsetDateTime.parse` 接受）。
- **错误提示**：复用 `getJson` 错误处理（Problem Details `errorCode` 显示中文标题，`requestId` 仅日志）；`REMINDER_TASK_INVALID_TRANSITION` 提示「任务状态已变化，已刷新」并重新拉取；`REMINDER_RULE_VERSION_CONFLICT` 提示并刷新版本号。
- **轮询节流**：未读计数 30s；进入页面立即拉一次；离开页面（`onBeforeUnmount`）清 `setInterval`。
- **无障碍**：状态点旁必有文字（不只靠颜色）；铃铛 `aria-label="通知 · 未读 N 条"`；表格行可键盘聚焦。

## 6. 测试策略

### 6.1 单元测试（Vitest + @vue/test-utils）

- `HomeView.spec.ts`：mock `api/reminder` 与 `api/inventory`，断言风险卡片数显示、优先任务列表渲染、快速操作入口存在、最近流水渲染。
- `RemindersView.spec.ts`：mock `fetchTasks`，断言筛选参数透传、状态点文字、操作下拉按 status 显示正确项；snooze 提交后刷新列表。
- `NotificationsView.spec.ts`：mock 通知 API，断言已读切换、全部已读。
- `ReminderRulesSettingsView.spec.ts`：mock `fetchRules/updateRules`，断言开关联动、天数降序校验、版本冲突刷新、member 角色隐藏保存按钮（用 `session.role` mock）。
- `AppShell.spec.ts`（追加）：mock `fetchUnreadCount`，断言角标显示、popover 渲染最近通知、setInterval 清理。

### 6.2 端到端（Playwright，追加到既有 e2e 套件）

- 「首页」场景：登录后 `/` 显示风险卡片与优先任务；点击低库存卡片跳转 `/reminders?kind=LOW_STOCK`。
- 「提醒中心」场景：在提醒中心对一条 OPEN 任务 snooze 1 小时 → 行状态变 SNOOZED + 显示 snoozedUntil → complete → 状态 DONE → reopen → OPEN。
- 「通知」场景：触发一次库存变更后（入库到期批次），等待轮询后顶栏角标 +1；点击全部已读角标归零。
- 「规则配置」场景：owner 进 `/settings/reminder` 关闭临期提醒 → 保存 → 提醒中心不再有该类临期任务（依赖 5a 后端 reconcile）。

## 7. 验收门槛

1. `npm --prefix frontend test` 全绿（Vitest 单元）。
2. `npm --prefix frontend run build`（含 typecheck）成功。
3. `make e2e-smoke` 通过追加的首页/提醒/通知/规则场景。
4. 浏览器实测：登录后首页直接见任务与风险；30s 内顶栏角标反映未读；rule 配置生效后提醒中心反映变化。
5. 无新增硬编码色值；视觉与「松间账册」一致。

## 8. 实施拆分（供 writing-plans）

1. API 客户端模块 `reminder.ts` + `notification.ts` + 类型。
2. 路由变更：`/` → HomeView、新增 `/system` `/reminders` `/notifications` `/settings/reminder`。
3. AppShell 启用 `提醒中心`、新增系统状态入口、顶栏通知铃铛组件。
4. `HomeView.vue` 风险卡片 + 优先任务 + 快速操作 + 最近流水。
5. `RemindersView.vue` 筛选栏 + 表格 + 行操作 + snooze 弹出 + 详情抽屉。
6. `NotificationsView.vue`。
7. `ReminderRulesSettingsView.vue`。
8. 单元测试 + 端到端追加。
9. 5b 收尾（`make frontend-test`、`make frontend-build`、`make e2e-smoke`、收尾记录）。
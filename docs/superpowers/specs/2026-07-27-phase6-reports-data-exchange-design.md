# 阶段六：报表与数据交换 设计方案

- **日期：** 2026-07-27
- **状态：** 已确认，作为阶段六实施计划与验收依据
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` §6.7（全局搜索）、§6.8（报表、导入与导出）、§7.1（报表与导出导航）、§7.2（CSV 导入/导出交互模式）、§8.3（复杂报表 SQL 自定义 Mapper）、§8.4（reporting 模块依赖）、§8.5（写入数据流与一致性）、§9（API）、§10（导入/导出审计）。
- **交付路线：** 阶段 6（报表与数据交换）。依赖阶段 1–5 全部交付（`system` / `identity` / `household` / `catalog` / `location` / `file` / `inventory` / `reminder`）。
- **关联决策：** ADR-004（reporting 事件投影 + 查询端口混合读模型）、ADR-005（事件管增量、拉取管重建）、ADR-006（`StockChangedEvent` 追加 `operatorAccountId` 等字段）。
- **关键更正：** 用户明确阶段六**不交付 CSV 导入**，仅交付 CSV 导出与全局搜索/报表。`CONTEXT.md` 中已移除「导入任务 / 预检导入 / 确认导入」相关术语；spec §6.8 关于 CSV 导入的两步法要求在本阶段不再适用。

## 1. 目标与边界

### 1.1 必须达成的结果

- 新增 `reporting` 模块，仅依赖 `household` / `catalog` / `location` / `inventory` / `system` 的公共 `Api`、公开事件与 DTO/记录。
- 全局搜索覆盖物品名称、品牌、标签、批次号、序列号、位置名，按实体类型分组返回；中文子串匹配。
- 报表覆盖 spec §6.8 列出的全部五项（当前库存与位置分布、临期、低库存、指定时间范围库存变化、按成员/操作类型/物品筛选流水），使用 MyBatis-Plus `Page<>` 分页与统一筛选参数，复杂报表 SQL 写在 `reporting` 自有 `Mapper XML`。
- 同步流式导出当前筛选结果与全集，CSV 含 UTF-8 BOM 以兼容 Excel 中文；不替代完整备份。
- 全局搜索与报表 UI 在侧边栏启用「报表与导出」入口；查询全员可访问，导出 OWNER/ADMIN 才能触发。
- 全部导出动作写 `EXPORT_PERFORMED` 审计；管理员手动触发的投影重建写 `REPORTING_PROJECTION_REBUILT` 审计。
- `ModularityTests` 通过；PostgreSQL 集成测试用 Testcontainers 验证投影初始化、事件增量更新、快照拉取重建与跨表报表查询。

### 1.2 阶段六明确不做

- CSV 导入（用户明确取消）。规格 §6.8 中「上传→预检→确认」两步法在本阶段不实现；导入相关数据流与 UI 留待后续阶段按需补齐。
- 全局拼音搜索、语义搜索、AI 助手（spec §3.3、§6.7）。
- 报表后台任务生成大文件（同步流式响应足以覆盖单家庭规模；超过 100,000 行时返回 400 + 引导改用更窄筛选）。
- 不同计量单位自动换算（spec §3.3）。
- 公开事件的字段重排或删除；公共 API 命令端口的导出路径复用现有 `requireAdmin` / `requireMember` 注解，不新增跨域/外部令牌。

## 2. 模块依赖与读模型架构

### 2.1 `reporting` 模块边界

```
com.zija.reporting/
  ReportingApi.java          # （可选）对外 DTO 锚点，阶段六首页不直接暴露为业务端点
  package-info.java          # @ApplicationModule allowedDependencies = "household","catalog","location","inventory","system"
  internal/
    <Reporting>Controller.java
    <Reporting>Service.java
    search/                  # SearchService、SearchMapper、search XML
    reports/                 # ReportService、各报表 XML
    export/                  # CsvWriter、ExportService
    projection/              # ProjectionListener、ProjectionRebuilder、processed_event、dead-letter
    persistence/             # Mapper、Entity、XML
```

依赖方向：`reporting → inventory / catalog / location / system / household` 的**公共 API 与公开事件**。绝不反向依赖，绝不读他模块 `internal/`。`reminder` 模块不依赖 `reporting`，反之亦然。

### 2.2 事件投影 + 快照拉取

详见 ADR-004 / ADR-005。要点：

| 投影表 | 来源事件 | 字段覆盖范围 | 重建来源（快照端口） |
|---|---|---|---|
| `reporting_search_index` | `ItemCreated/Updated/Archived/CategoryChanged/BrandChanged/UnitChanged/TagChanged`（catalog 新增）、`LocationCreated/Updated/Moved/Restored/Deleted`（location 新增） | `household_id`、`item_id`、`brand_name`、`tag_names`、`category_name`、`unit_name`、`lot_id`、`lot_number`、`serial_number`、`location_id`、`location_name`、`location_path` | `CatalogApi.dumpItems(householdId, cursor)` / `LocationApi.dumpTree(householdId, cursor)` |
| `reporting_stock_flat` | `StockChangedEvent` | 投影字段同下方「事件契约扩展」 | `InventoryApi.dumpStockPositions(householdId, cursor)` |
| `reporting_movement_flat` | `StockChangedEvent` | 同上 | `InventoryApi.dumpMovements(householdId, cursor)` |
| `reporting_location_flat` | Location 变更事件 | `household_id`、`location_id`、`parent_id`、`name`、`path`、`sort_order`、`status` | `LocationApi.dumpTree` |

> 物品级变更（item 档案更新、归档、分类/品牌/单位/标签变更）由 `catalog` 模块在阶段六新增 `CatalogEventPublisher` 发布的 `ItemChangedEvent`、`CategoryChangedEvent`、`BrandChangedEvent`、`UnitChangedEvent`、`TagChangedEvent`。位置级变更由 `location` 模块新增 `LocationEventPublisher` 发布 `LocationChangedEvent` / `LocationMovedEvent` / `LocationDeletedEvent`。所有事件字段只追加、不重排、不删除（ADR-006 约束）。

### 2.3 投影初始化与重建

- `reporting` 启动时检查每张投影表是否为空；若为空，按 household 维度拉取快照填充（首阶段一次性 backfill）。
- 暴露管理员端点 `POST /api/v1/reporting/projection/rebuild?householdId={id}`：清空指定家庭相关投影行，重新走快照拉取；写 `REPORTING_PROJECTION_REBUILT` 审计。
- 重建期间事件订阅**仍持续运行**，事件按 `event_publication` 顺序落到投影表；重建清空不会破坏后续事件送达，因为去重依据 `processed_event.event_id`。
- 重建支持游标分批，每批一个独立短事务，避免长时间持锁。

## 3. 公共事件契约扩展

> ADR-006。本节为该 ADR 的实施规约。

### 3.1 `StockChangedEvent` 字段扩展

```java
public record StockChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID lotId,
        UUID itemId,
        String movementType,
        BigDecimal quantityDelta,
        UUID fromLocationId,
        UUID toLocationId,
        OffsetDateTime businessTime,
        UUID movementId,
        UUID idempotencyKey,
        // 阶段六追加：
        UUID operatorAccountId,
        String reason,
        UUID reversalOf
) {}
```

- `operatorAccountId`、`reason`、`reversalOf` 在 `StockCommandService` / `ReversalService` / `StocktakeService` 内部从 `MovementEntity` 已有字段填充；`null` 合法（如系统自动调整）。
- 已有消费者 `ReminderEventListener` 与 `EventRetryService` 必须同步更新：
  - `toMap` 增加 `operatorAccountId` / `reason` / `reversalOf` 三个键（缺键视为 `null`）。
  - `fromMap` 调整构造器位置与字段，补 `null` 兜底；dead-letter JSONB 旧 payload 缺键时容错。
  - 迁移阶段完成前不得清理历史 `reminder_event_dead_letter` 行。
- 公共事件字段只追加、不重排、不删除——记录为跨模块合约，未来读者不得重命名或裁剪。

### 3.2 新增 catalog / location 事件

为减少事件数量，**统一为粗粒度 `XxxChangedEvent`**：

| 模块 | 新增事件 record | 关键字段 |
|---|---|---|
| catalog | `ItemChangedEvent(UUID eventId, UUID householdId, UUID itemId, String changeType /* CREATED/UPDATED/ARCHIVED/RESTORED */, OffsetDateTime businessTime)` | item_id、changeType |
| catalog | `CategoryChangedEvent(UUID eventId, UUID householdId, UUID categoryId, String changeType)` | category_id |
| catalog | `BrandChangedEvent(...)` / `UnitChangedEvent(...)` / `TagChangedEvent(...)` | 同上 |
| location | `LocationChangedEvent(UUID eventId, UUID householdId, UUID locationId, String changeType /* CREATED/UPDATED/RENAMED/MOVED/DELETED */, UUID parentId, OffsetDateTime businessTime)` | location_id、parentId |

`changeType` 字符串约定大写、与 `inventory_movement.type` 风格保持一致。`catalog` / `location` 模块新增 `CatalogEventPublisher` / `LocationEventPublisher`，注入 `ApplicationEventPublisher`，与 `InventoryEventPublisher` 同模式。

> 字段再次只追加、不重排、不删除。

## 4. 公共 API 新增

### 4.1 `InventoryApi` 追加

```java
/** 增量拉取家庭库存位（按 updated_at 游标分批）。仅供 reporting 投影重建。 */
PageDump dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit);

/** 增量拉取家庭全部库存流水（按 created_at 游标分批）。 */
PageDump dumpMovements(UUID householdId, OffsetDateTime cursor, int limit);

record PageDump(List<? extends Info> items, OffsetDateTime nextCursor, boolean hasMore) {}
```

实现细节：
- `dumpStockPositions` / `dumpMovements` 在 `inventory/internal/persistence/` 下放 Mapper XML，使用 `(household_id, updated_at > cursor) ORDER BY updated_at ASC LIMIT :limit`，游标为上一批最后一条的 `updated_at`；空集返回 `hasMore=false`。
- 该方法**仅供 reporting 模块使用**，记录在 javadoc 上，限制为包级 `@VisibleForTesting` 等价注释；不暴露 REST 端点。

### 4.2 `CatalogApi` 追加

```java
/** 增量拉取家庭物品（含品牌、分类、单位、标签 join 后扁平化）。仅供 reporting 投影重建。 */
List<ItemFlat> dumpItems(UUID householdId, OffsetDateTime updatedSince, int limit);
```

`ItemFlat` 包含搜索/导出所需的全部字段（详见 §5 投影 schema）。

### 4.3 `LocationApi` 追加

```java
/** 增量拉取家庭位置树扁平化（含 path）。仅供 reporting 投影重建。 */
List<LocationFlat> dumpTree(UUID householdId, OffsetDateTime updatedSince, int limit);
```

`LocationFlat` 含 `path` 字段（用 `>` 连接父节点名称）。

### 4.4 公共 API 不变约束

- `ReportingApi` 不引入命令端口；reporting 仅消费 `inventory` / `catalog` / `location` / `system` 的只读能力。
- `IdentityApi` / `HouseholdApi` 已有 `MemberInfo`、`findMembers`、`hasAtLeastRole`、`requireActiveMember`，足够支撑搜索/报表操作人展示与角色校验。

## 5. reporting 投影表 schema（V5 迁移）

```sql
-- V5__create_reporting_core.sql

-- 1) reporting 事件去重（与 reminder 隔离）
CREATE TABLE reporting_processed_event (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_processed_event_type ON reporting_processed_event(event_type);

-- 2) reporting 事件 dead-letter
CREATE TABLE reporting_event_dead_letter (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL,
    event_type      VARCHAR(80) NOT NULL,
    payload         JSONB NOT NULL,
    failure_count   INTEGER NOT NULL DEFAULT 1,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    last_error      VARCHAR(4000),
    last_retry_at   TIMESTAMPTZ,
    abandoned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reporting_dead_letter_event UNIQUE (event_id)
);
CREATE INDEX idx_reporting_dead_letter_retry ON reporting_event_dead_letter(abandoned, next_retry_at);

-- 3) 全局搜索扁平读模型
CREATE TABLE reporting_search_index (
    household_id    UUID NOT NULL,
    entity_type     VARCHAR(20) NOT NULL,        -- ITEM | LOT | LOCATION
    entity_id       UUID NOT NULL,
    brand_name      VARCHAR(120),
    tag_names       VARCHAR(400),
    category_name   VARCHAR(120),
    unit_name       VARCHAR(40),
    lot_number      VARCHAR(120),
    serial_number   VARCHAR(120),
    location_path   VARCHAR(800),
    -- 仅 ITEM 行：物品名
    item_name       VARCHAR(120),
    -- 仅 LOCATION 行：位置名
    location_name   VARCHAR(120),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, entity_type, entity_id)
);
CREATE INDEX idx_reporting_search_item_name
    ON reporting_search_index(household_id, entity_type, item_name);
CREATE INDEX idx_reporting_search_lot_number
    ON reporting_search_index(household_id, entity_type, lot_number);
CREATE INDEX idx_reporting_search_location
    ON reporting_search_index(household_id, entity_type, location_path);

-- 4) 库存流水扁平读模型
CREATE TABLE reporting_movement_flat (
    household_id       UUID NOT NULL,
    movement_id        UUID NOT NULL PRIMARY KEY,
    event_id           UUID NOT NULL UNIQUE,
    lot_id             UUID NOT NULL,
    item_id            UUID NOT NULL,
    item_name          VARCHAR(120) NOT NULL,
    type               VARCHAR(20) NOT NULL,
    quantity_delta     NUMERIC(20,6) NOT NULL,
    from_location_id   UUID,
    to_location_id     UUID,
    from_location_path VARCHAR(800),
    to_location_path   VARCHAR(800),
    operator_account_id UUID,
    operator_display_name VARCHAR(120), -- 由 reporting 拉取 identity 信息同步缓存，事件不带展示字段
    reason             VARCHAR(120),
    business_time      TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_movement_flat_time
    ON reporting_movement_flat(household_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_item
    ON reporting_movement_flat(household_id, item_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_operator
    ON reporting_movement_flat(household_id, operator_account_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_type
    ON reporting_movement_flat(household_id, type, business_time DESC);

-- 5) 库存位扁平读模型（仅作为报表衍生：导出 / 位置分布 / 临期）
CREATE TABLE reporting_stock_flat (
    household_id       UUID NOT NULL,
    lot_id             UUID NOT NULL,
    item_id            UUID NOT NULL,
    item_name          VARCHAR(120) NOT NULL,
    unit_name          VARCHAR(40) NOT NULL,
    lot_number         VARCHAR(120),
    serial_number      VARCHAR(120),
    expiry_date        DATE,
    location_id        UUID NOT NULL,
    location_path      VARCHAR(800) NOT NULL,
    quantity           NUMERIC(20,6) NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, lot_id, location_id)
);
CREATE INDEX idx_reporting_stock_flat_expiry
    ON reporting_stock_flat(household_id, expiry_date)
    WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_reporting_stock_flat_item
    ON reporting_stock_flat(household_id, item_id);

-- 6) 重建任务审计字段直接复用 audit_log，无需新建表
```

> 「lowStockThreshold」与「expiryReminderDays」不入库存位扁平表——低库存与临期报表查询时 join `reporting_search_index` 上的物品维度信息或在 SQL 内聚合；阈值保留在 `catalog_item` 上，由 reporting 用 `ItemFlat` 快照缓存或每次实时 join 一次，权衡后选实时 join（数据量小）。

## 6. 后端实现

### 6.1 事件监听器

`reporting/projection/ProjectionListener`：
- 四个 `@EventListener`：`onStockChanged`、`onItemChanged`、`onCategoryChanged`、`onBrandChanged`、`onUnitChanged`、`onTagChanged`、`onLocationChanged`。
- 进入时先 `INSERT INTO reporting_processed_event ... ON CONFLICT DO NOTHING`；返回 0 行表示已处理，return。
- 在独立事务（`REQUIRES_NEW`）内 upsert 到对应投影表。
- 失败写 `reporting_event_dead_letter` 并删除刚插入的 `processed_event`，允许重试。
- `ReportingEventRetryService` 用 `@Scheduled` 定时轮询 `abandoned=false AND next_retry_at <= now()` 的死信，每 30 秒尝试一次，指数退避；失败累计 `failure_count`；超过 10 次置 `abandoned=true` 并写审计 `REPORTING_EVENT_ABANDONED`。

### 6.2 全局搜索

- 端点：`GET /api/v1/reporting/search?q={keyword}&limitPerGroup=5`。
- 行为：在 `reporting_search_index` 用 ILIKE `'%' || :q || '%'` 对物品名/品牌/标签/批次号/序列号/位置 path 分别匹配；命中按实体类型（`ITEM` / `LOT` / `LOCATION`）分组返回，每组 `limitPerGroup` 条（默认 5、上限 20）。
- 响应：

```json
{
  "items": [
    { "itemId": "...", "name": "...", "brand": "...", "tags": ["..."], "category": "...", "matchedFields": ["name","tag"] }
  ],
  "lots": [
    { "lotId": "...", "itemId": "...", "itemName": "...", "lotNumber": "...", "serialNumber": "...", "matchedFields": ["lotNumber"] }
  ],
  "locations": [
    { "locationId": "...", "name": "...", "path": "厨房>冰箱>冷藏室", "matchedFields": ["path"] }
  ]
}
```

- 不命中返回空数组；字段命中用 `matchedFields: ["name"]` 列出，便于前端高亮。
- 中文子串匹配，不分词、不拼音、不语义匹配；性能靠单家庭数据量小的实际场景。

### 6.3 报表端点

统一分页参数 `page=1&pageSize=20`（与现有库存页保持一致；上限 100）；筛选按各报表维度。

| 方法 | 路径 | 角色 | 描述 |
|---|---|---|---|
| GET | `/api/v1/reporting/reports/stock-by-location` | 成员 | 当前库存与位置分布：按位置 + 物品聚合；筛选 `itemId` / `categoryId` / `locationId` / `brandId` |
| GET | `/api/v1/reporting/reports/expiring-lots` | 成员 | 临期批次：筛选 `withinDays`（默认 30）/ `itemId` / `locationId` |
| GET | `/api/v1/reporting/reports/low-stock` | 成员 | 低库存物品：聚合各物品当前总库存；筛选 `categoryId` |
| GET | `/api/v1/reporting/reports/stock-changes` | 成员 | 指定时间范围库存变化：筛选 `from` / `to` / `itemId` / `locationId` / `type` |
| GET | `/api/v1/reporting/reports/movements` | 成员 | 按成员/类型/物品筛选流水：筛选 `from` / `to` / `itemId` / `type` / `operatorAccountId` |

复杂报表 SQL 写在 `reporting/internal/persistence/ReportMapper.xml`（spec §8.3）。

### 6.4 导出端点

| 方法 | 路径 | 角色 | 描述 |
|---|---|---|---|
| GET | `/api/v1/reporting/exports/{reportKey}` | OWNER/ADMIN | 同步流式响应。`reportKey` ∈ `stock-by-location | expiring-lots | low-stock | stock-changes | movements | items-full | lots-full | locations-full`。支持 `from` / `to` / `itemId` / `locationId` / `type` / `operatorAccountId` / `scope=current-filter|full` 参数（`scope=full` 忽略筛选） |
| POST | `/api/v1/reporting/projection/rebuild` | OWNER/ADMIN | 触发指定家庭投影重建 |

- 输出 CSV，UTF-8 BOM + `Content-Type: text/csv; charset=utf-8` + `Content-Disposition: attachment; filename="..."`。
- 使用 `StreamingResponseBody` 直接写 `HttpServletResponse` 输出流；不写临时文件、不入库。
- 行数硬上限 100,000；超过返回 `400 REPORTING_EXPORT_TOO_LARGE` + Problem Details 引导缩小筛选。
- 每次导出写审计 `EXPORT_PERFORMED`：`action=EXPORT_PERFORMED, outcome=SUCCESS, detail={reportKey, scope, rowCount, fileName}`。
- 失败也写 `EXPORT_PERFORMED` outcome=`FAILURE`。

### 6.5 错误码

沿用 spec §9 Problem Details 约定；新增稳定错误码：

- `REPORTING_PROJECTION_REBUILD_FAILED`
- `REPORTING_EXPORT_TOO_LARGE`
- `REPORTING_EVENT_ABANDONED`（用于审计查询过滤展示）
- `REPORTING_PROHIBITED`（非 admin 触发导出时复用现 `@RequireAdmin` 拒绝；不新增）

## 7. 前端实现

### 7.1 路由与导航

- 启用侧边栏「报表与导出」入口，路径 `/reports`。
- 子路由：
  - `/reports/search`：全局搜索结果页（含最近搜索与提示）。
  - `/reports/stock-by-location`：当前库存与位置分布报表。
  - `/reports/expiring-lots`：临期批次报表。
  - `/reports/low-stock`：低库存物品报表。
  - `/reports/stock-changes`：库存变化（时间范围）。
  - `/reports/movements`：流水筛选（成员/类型/物品）。
  - `/reports/settings`：管理员页 — 投影重建按钮 + 导出审计查询。

### 7.2 页面与交互

- 列表页统一沿用阶段二/三/四约定的固定筛选栏 + `Element Plus` 表格 + 分页。
- 报表页：
  - 顶部筛选条 `surface-sunken` 底；筛选项同端点参数；提交立即重新查询。
  - 表头数字列 `tabular-nums`。
  - 行点击进入物品/批次/位置详情（沿用既有详情抽屉）。
  - 表右上「导出 CSV」按钮（仅 OWNER/ADMIN 可见；点击触发 `/reporting/exports/{key}` 同筛选 GET，浏览器原生下载）。
  - 表下方显示结果总数与本次查询耗时（可选 `tabular-nums`）。
- 搜索页 `/reports/search`：
  - 单行输入框（防抖 250ms）；回车与点击「搜索」都触发。
  - 结果按实体类型分三个折叠组（`Items` / `Lots` / `Locations`），默认各显示前 5 条，可展开。
  - 每条命中卡片右下角小标签 `matchedFields`。
- `ReportingSettingsView.vue`（仅 OWNER/ADMIN）：
  - 「重建报表读模型」按钮：弹窗二次确认；提交后调 `POST /reporting/projection/rebuild`；完成后显示最近重建时间 + 行数。
  - 「导出审计」表：调 `GET /api/v1/audit-logs?action=EXPORT_PERFORMED`（复用已有端点）。

### 7.3 视觉

沿用「松间账册」体系（`tokens.css` / `index.css`），组件内禁止硬编码色值。报表表格数字列 `tabular-nums`；空态用现有 `.empty-state` 占位；危险操作（重建）走 `el-popconfirm`。

## 8. 测试策略

### 8.1 后端单元测试

- 投影 upsert SQL：`ProjectionListenerTest` —— 给定不同事件 → 投影表期望行；`processed_event` 去重。
- 报表 XML SQL：每个 ReportMapper 方法对照 fixtures 验证行集、列、排序、分页。
- 搜索 ILIKE：`SearchServiceTest` —— 多类实体命中、命中字段标记、空结果。
- CSV 写出：`CsvWriterTest` —— UTF-8 BOM、换行、引号转义、空值、行数上限。

### 8.2 PostgreSQL 集成（Testcontainers）

- 端到端：建家 → 入库 → 等待 `StockChangedEvent` 投递 → 断言 `reporting_movement_flat` 行；`reporting_processed_event` 已写入；同一 `eventId` 重发不重复落库。
- 重建：清空 `reporting_movement_flat` → `POST /projection/rebuild` → 断言行回填且事件订阅未停。
- 跨表报表：临期报表 join `reporting_stock_flat` + `reporting_search_index` 拿物品名；低库存 join 阈值。
- 导出 CSV 大响应（10 万行）性能：测试流式响应在容器环境下不 OOM。

### 8.3 前端

- Vitest：搜索防抖、分组折叠、导出按钮权限显隐、`EXPORT_PERFORMED` 审计表展示。
- Playwright（`make e2e-smoke`）：完整链路 —— 入库 → 等待投影 → 报表页看到该批次 → 导出 CSV → CSV 行内容核对。

### 8.4 模块边界

- `ModularityTests` 新增 `reportingModuleExistsAndDependenciesAreValid`：验证 `reporting` 仅依赖 `household` / `catalog` / `location` / `inventory` / `system`，且仅通过 `Api` 与公开事件与他模块交互。
- 在 `internal/` 上 `@VisibleForTesting` 等价注释防止误用；现有测试套件无回归。

## 9. 验收门槛

1. `make verify` 通过（后端测试、前端测试、`frontend-build`、`backend-build`、`git diff --check`）。
2. `make compose-smoke` 通过；`make e2e-smoke` 含全局搜索、报表查询、CSV 导出、投影重建场景。
3. `ModularityTests.reportingModuleExistsAndDependenciesAreValid` 通过。
4. PostgreSQL 集成测试（Testcontainers）覆盖：事件增量→投影；快照拉取重建；跨表报表；CSV 流式导出 100,000 行。
5. 审计含 `EXPORT_PERFORMED`（含 success / failure）、`REPORTING_PROJECTION_REBUILT`、`REPORTING_EVENT_ABANDONED`。
6. 阶段六启动后历史流水已通过快照拉取端口补齐；「指定时间范围库存变化」报表展示阶段六上线前所有流水。
7. 阶段六关闭后，`CONTEXT.md` 反映本次会话新增的术语与决策；`docs/adr/004–006` 完整登记。

## 10. 实施拆分（供 writing-plans）

建议拆为 3 个子任务：

1. **6a reporting 后端基础与投影**：`reporting` 模块骨架 + 公共事件扩展（§3）+ V5 迁移（§5）+ `ProjectionListener` + `ReportingEventRetryService` + 快照拉取端口扩展（§4）。
2. **6b 报表与导出后端**：`ReportMapper` XML + 5 张报表端点（§6.3）+ 导出端点（§6.4）+ CSV 写出 + 行数上限 + 审计；后端单元 + Testcontainers 集成。
3. **6c 前端报表/搜索/导出 UI**：路由启用、5 张报表页、搜索页、设置页（重建按钮）、导出审计表；Playwright 端到端。

每个子任务在执行前需各自获得一份独立的实施计划文件；不得仅凭本设计文档启动实施。

## 11. 已确认关键决策

1. **阶段六不交付 CSV 导入**，仅交付 CSV 导出与全局搜索/报表（用户明确更正）。
2. **reporting 采用事件投影 + 快照拉取端口的混合读模型**（ADR-004）：事件管增量，快照管重建。
3. **catalog / location 新增粗粒度 `XxxChangedEvent`**，不引入细粒度列级事件；reporting 据此维护 `reporting_search_index` / `reporting_location_flat`。
4. **`StockChangedEvent` 追加 `operatorAccountId` / `reason` / `reversalOf` 字段**，reminder listener 与 dead-letter 反序列化器同步改造（ADR-006）。
5. **全局搜索实现**为 ILIKE '%词%'，不引入 `pg_trgm` / 中文分词 / 拼音；单家庭数据量足够。
6. **搜索结果按实体类型分组**返回（items / lots / locations），每组上限 20，默认 5。
7. **报表/搜索全员可读，导出仅 OWNER/ADMIN**（spec §4.2 角色矩阵）。
8. **导出同步流式响应**，行数硬上限 100,000；不引入后台任务表。
9. **reporting 自有 `processed_event` / `dead-letter` / `EventRetryService`**，与 reminder 同模式但隔离；不重构 reminder 既有代码。
10. **复杂报表 SQL** 写在 `reporting` 自有 `Mapper XML`（spec §8.3）；不跨模块直连他表。
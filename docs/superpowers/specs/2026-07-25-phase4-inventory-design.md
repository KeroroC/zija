# 阶段四：库存流水设计方案

- **状态：** 待审批
- **日期：** 2026-07-25
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` 第 5.3、6.4–6.5、8.4–8.5、9–12 节（仅 §11.2、§12.1 阶段四相关条目）
- **交付路线：** 阶段 4（库存流水），依赖已交付的阶段 1–3（`system` / `identity` / `household` / `catalog` / `location` / `file` 模块，迁移至 V10）

## 1. 目标、状态和事实来源

### 1.1 必须达成的结果

- 活跃家庭成员可在桌面端完成批次入库、补充现有批次、领用、报损、移位和盘点全部库存主链路操作。
- 每个库存命令在单个数据库事务内产出不可变库存流水，并更新对应库存位；任意库存位数量都可由有效流水汇总重建。
- 并发消费者无法超支同一库存位、无法重复扣减、无法造成负库存。
- Owner/Admin 可对错误流水执行冲正，冲正只生成补偿流水并保留原始记录，不修改也不删除原始流水。
- Owner/Admin 可发起内部一致性检查，按流水重新计算并比对库存位，发现差异时只报告不自动篡改流水或库存位。
- 所有写入接口接受幂等标识，同一幂等键加相同请求返回首次结果且不产生新流水。
- 新增 `inventory` 模块，仅依赖 `household`、`catalog`、`location` 的公开 `Api` 与 DTO/事件，模块边界由 `ModularityTests` 验证。
- API、前端、PostgreSQL 集成、Playwright、Compose 冒烟和模块验证测试覆盖主流程及失败分支。

### 1.2 事实来源

- 产品与架构规格：`docs/superpowers/specs/2026-07-18-zija-design.md`（§5.3 库存流水模型、§6.4 入库、§6.5 领用/报损/盘点/移位、§8.4 后端模块、§8.5 写入数据流、§9 API 设计、§10 安全与审计、§11.2 并发库存操作、§12 质量策略）。
- 交付路线：`docs/superpowers/plans/2026-07-19-delivery-roadmap.md`（阶段四验收门槛）。
- 现有公开契约：`CatalogApi`（`requireActiveItem` / `requireItem` / `requireUnit` 及 `ItemInfo`、`UnitInfo`）、`LocationApi`（`requireLocation` / `markReferenced` / `tree` 及 `LocationInfo`）、`HouseholdApi`（`requireActiveMember` / `hasAtLeastRole` / `MemberRole`）、`SystemApi`（`recordAudit` / `AuditEvent`）。

## 2. 范围与排除项

### 2.1 在范围内

- `inventory` 模块：批次 `Lot`、库存位 `StockPosition`、库存流水 `Movement`、盘点草稿与确认 `Stocktake`、幂等命令登记 `IdempotencyRecord`、公开库存变更事件 `StockChangedEvent`、内部一致性检查。
- 库存工作流：入库（新建批次与补充现有批次）、领用、报损、移位、盘点（草稿/确认/取消）、冲正、一致性检查。
- 桌面端：启用侧边栏「库存管理」入口与 `/inventory` 路由；四个页签（当前库存、批次、流水、盘点）；分步对话框承载入库/领用/报损/移位；AppShell 顶栏「库存操作」下拉；物品详情和位置详情的库存摘要与入口。
- 数据库迁移：仅前进的 Flyway 迁移（V11 及之后）。

### 2.2 明确不在阶段四范围

- 临期、低库存提醒任务和站内通知（阶段五）。阶段四不生成任务，不发布提醒事件，但成功库存命令已发布带唯一事件 ID 的公开 `StockChangedEvent`，供阶段五可靠订阅。
- 阶段五提醒监听器的业务实现；阶段四建立可靠发布契约（Spring Modulith 事务内登记 + 事务提交后投递），但不实现阶段五消费者。
- 报表、CSV 导入导出和全局搜索（阶段六）。阶段四只提供库存查询端口，不实现导出/导入/搜索。
- 自动库存修复、自动盘点、按规则关闭任务（阶段五/六）。一致性检查只报告差异，不修改库存。
- 多单位换算、条码、OCR、移动端适配、票据与说明书及批次附件。
- 库存位的物理删除或流水的物理删除/编辑。归档物品不再新增库存，但已有库存仍可正常操作。

## 3. 已确认关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 业务模块 | 新增独立 `inventory` 模块，内部包承载实现 | 批次、流水、库存位与盘点具有独立生命周期；单向依赖 `household`/`catalog`/`location`/`system`，符合总体模块图。 |
| 权限 | 所有活跃成员可查看并执行入库、领用、报损、移位、盘点；仅 Owner/Admin 可冲正和发起一致性检查 | 与规格 §4.2 角色矩阵一致；后端再次校验，前端隐藏按钮不构成安全边界。 |
| 物品状态约束 | 新建批次及所有入库必须要求物品为 `ACTIVE`；归档物品不得新增库存，但已有库存仍可领用、报损、移位、盘点和冲正 | 防止向已停用物品继续灌库存，同时保留历史可操作链路。 |
| 批次归属不可变 | 批次物品归属创建后不可修改；批次日期、编号、序列号、备注可通过版本号修正并审计 | 流水只引用批次，不可因批次改物而错配库存位。 |
| 序列号重复 | 同一物品下序列号重复只警告，不硬性禁止 | 耐用品登记低摩擦，由人工核对。 |
| 数量精度 | 数量必须符合 `CatalogApi.UnitInfo.decimalScale` | 与 `catalog` 模块的唯一权威单位定义一致；后端在命令处理前校验。 |
| 领用批次推荐 | 按到期日升序推荐批次，无到期日排在最后；用户最终明确选择批次和位置 | 落实「先到期先使用」原则但不剥夺用户选择。 |
| 报损 | 原因必填，备注可选 | 与规格 §6.5 一致。 |
| 移位 | 来源与目标不能相同，两边在同一事务完成；一条 `TRANSFER` 流水同时更新两个库存位 | 与规格 §5.3、§6.5 一致；避免单边提交。 |
| 盘点 | 可保存草稿；差异条目原因必填；可补录已有批次且账面为零的条目；全新批次先走入库，再刷新盘点 | 草稿避免误提交；原因可追溯；补录覆盖漏登记批次。 |
| 事务边界 | 每个库存命令在单个数据库事务内完成；流水、库存影响、库存位、幂等结果、审计和公开事件原子提交 | 与规格 §8.5 一致，强一致边界。 |
| 锁顺序 | 先按 UUID 固定顺序锁定相关批次，再用显式 `SELECT ... FOR UPDATE` 锁定库存位 | 覆盖目标库存位尚不存在的并发创建场景并降低死锁风险；不依赖通用乐观锁插件。 |
| 幂等 | 同一幂等键加相同请求返回首次结果，不产生新流水；同一幂等键加不同请求返回 `409 INVENTORY_IDEMPOTENCY_CONFLICT`；失败回滚不留下成功幂等记录 | 与规格 §9、§11.2 一致，覆盖网络重试和重复点击。 |
| 盘点快照 | 草稿快照记录范围内库存位的 revision；确认时比较整个范围的库存位集合与 revision | 整单守恒；范围发生任何库存变化则确认失败。 |
| 冲正 | 只创建等量反向 `REVERSAL` 流水并引用原流水，保留原始记录；不允许对已冲正或会致负的流水冲正 | 与规格 §5.3、§6.5 一致，流水真且不可改。 |
| 公开事件 | 成功库存命令发布带唯一事件 ID 的 `StockChangedEvent`；阶段四建立可靠发布契约，不实现阶段五消费者 | 为阶段五预留接入点，本阶段只验投递与去重。 |
| 桌面交互 | 启用侧边栏「库存管理」入口，`/inventory` 四页签；分步对话框承载出入库操作；沿用「松间账册」视觉体系；服务端数据不进入长期 Pinia 缓存 | 与规格 §7、阶段三桌面信息架构一致。 |
| 幂等键使用 | 网络重试复用同一幂等键；业务内容改变后生成新键 | 客户端无需感知服务端状态即可保证正确重试。 |
| 阶段四范围守恒 | 只展示日期和库存事实，不生成临期/低库存任务或通知 | 严格切分阶段四与阶段五。 |

## 4. 模块架构与依赖

### 4.1 模块结构

```
com.zija.inventory/
  InventoryApi.java                 # 公开 API：库存查询、批次查询、公开事件类型
  package-info.java                 # @ApplicationModule
  internal/
    InventoryController.java         # REST 端点
    InventoryService.java            # 命令编排、权限校验、事务边界
    InventoryExceptionHandler.java   # Problem Details 映射
    LotService.java                  # 批次创建与修正
    StockCommandService.java         # 入库/领用/报损/移位命令
    StocktakeService.java            # 盘点草稿与确认
    ReversalService.java             # 冲正
    ConsistencyCheckService.java     # 仅查询比对，不写库存
    IdempotencyService.java          # 幂等键比对
    event/
      StockChangedEvent.java         # 公开事件记录
      InventoryEventPublisher.java   # 事务内登记 + 提交后投递
    persistence/
      LotMapper.java / LotEntity.java
      StockPositionMapper.java / StockPositionEntity.java   (含 revision 乐观版本)
      MovementMapper.java / MovementEntity.java
      StocktakeMapper.java / StocktakeEntity.java
      StocktakeItemMapper.java / StocktakeItemEntity.java
      IdempotencyRecordMapper.java / IdempotencyRecordEntity.java
      ConsistencyCheckMapper.java    # 自定义 XML: 行锁、聚合比对
    *Mapper.xml                      # 显式 SELECT ... FOR UPDATE、条件更新、聚合
```

### 4.2 依赖规则

- `inventory` 仅依赖 `household`、`catalog`、`location` 的公开 `Api` 与公开 DTO/事件，以及 `system` 的 `SystemApi` 记录审计。
- 禁止反向依赖：`catalog`、`location` 不得依赖 `inventory`。
- `inventory` 通过 `LocationApi.markReferenced(...)` 在首次入库写库存位时标记位置被引用，使 `location` 模块据此拒绝删除。
- `inventory` 通过 `CatalogApi.requireActiveItem(...)` 在新建批次与所有入库时强制物品状态；领用/报损/移位/盘点/冲正对归档物品仍可处理（使用 `requireItem` 而非 `requireActiveItem`）。
- 跨模块只交换公开记录类型；实体、Mapper、XML 不得泄漏出 `internal` 包。`ModularityTests` 持续验证依赖方向。

### 4.3 公开契约（`InventoryApi`）

`InventoryApi` 暴露给未来 `reminder`、`reporting` 模块的最小只读端口与公开事件类型，不含命令：

```java
public interface InventoryApi {
    Optional<StockPositionInfo> findStockPosition(UUID householdId, UUID lotId, UUID locationId);
    List<StockPositionInfo> stockPositionsOfItem(UUID householdId, UUID itemId);
    List<MovementInfo> movementsOfLot(UUID householdId, UUID lotId);

    record StockPositionInfo(UUID lotId, UUID locationId, java.math.BigDecimal quantity, long revision, OffsetDateTime updatedAt) {}
    record MovementInfo(UUID id, UUID lotId, UUID itemId, String type, java.math.BigDecimal quantity,
                        UUID fromLocationId, UUID toLocationId, String reason, UUID operatorAccountId,
                        OffsetDateTime businessTime, OffsetDateTime createdAt, UUID idempotencyKey, UUID reversalOf) {}
}
```

`StockChangedEvent`（公开事件，Spring Modulith `@ApplicationModuleListener` 订阅）：

```java
public record StockChangedEvent(
        UUID eventId,            // 事件唯一 ID，用于消费者去重
        UUID householdId,
        UUID lotId,
        UUID itemId,
        String movementType,     // INBOUND/CONSUME/LOSS/ADJUSTMENT/TRANSFER/REVERSAL
        java.math.BigDecimal quantityDelta,
        UUID fromLocationId,
        UUID toLocationId,
        OffsetDateTime businessTime,
        UUID movementId,
        UUID idempotencyKey) {}
```

## 5. 数据库与领域模型

### 5.1 迁移编号

阶段四起始迁移为 `V11__inventory_core.sql`（及其后续前向迁移）。所有迁移仅前进、幂等安全，不修改既有 V1–V10。

### 5.2 表设计要点

`inventory_lot`（批次）：
- `id UUID PK`，`household_id UUID NOT NULL`，`item_id UUID NOT NULL`（外键同家庭到 `catalog_item`）。
- `purchase_date`、`production_date`、`expiry_date`均为 `DATE NULL`；`lot_number VARCHAR(80) NULL`、`serial_number VARCHAR(120) NULL`、`memo VARCHAR(4000) NULL`。
- `created_at`、`updated_at TIMESTAMPTZ`，`version INTEGER NOT NULL DEFAULT 0`（修正批次资料时乐观锁）。
- 唯一约束：`UNIQUE (household_id, id)`。`item_id` 创建后不可更新（服务层强制）。
- 序列号重复不设数据库唯一约束，仅服务层返回警告标志。

`inventory_stock_position`（库存位，投影）：
- `id UUID PK`，`household_id UUID NOT NULL`，`lot_id UUID NOT NULL`，`location_id UUID NOT NULL`。
- `quantity NUMERIC(20,6) NOT NULL`，`revision BIGINT NOT NULL DEFAULT 0`（每次更新自增，盘点快照引用）。
- `created_at`、`updated_at TIMESTAMPTZ`。
- `UNIQUE (household_id, lot_id, location_id)`；`CHECK (quantity >= 0)`。
- 行锁通过自定义 XML `SELECT id, quantity, revision FROM inventory_stock_position WHERE household_id=? AND lot_id=? AND location_id=? FOR UPDATE`。

`inventory_movement`（不可变流水）：
- `id UUID PK`，`household_id UUID NOT NULL`，`lot_id UUID NOT NULL`，`item_id UUID NOT NULL`（冗余以加速查询，由批次所属物撑投影，与批次变更无关）。
- `type VARCHAR(20) NOT NULL` ∈ `{INBOUND, CONSUME, LOSS, ADJUSTMENT, TRANSFER, REVERSAL}`。
- `quantity NUMERIC(20,6) NOT NULL`（`REVERSAL` 可为正或负的等量反向值）。
- `from_location_id UUID NULL`、`to_location_id UUID NULL`（`TRANSFER` 两端都填；`INBOUND` 只填 `to`；`CONSUME`/`LOSS` 只填 `from`；`ADJUSTMENT` 视符号填对应端；`REVERSAL` 复制原流水的端点）。
- `reason VARCHAR(120) NULL`（`LOSS`、`ADJUSTMENT` 差异条目必填；`CONSUME` 可选），`memo VARCHAR(4000) NULL`。
- `operator_account_id UUID NOT NULL`；`business_time TIMESTAMPTZ NOT NULL`、`created_at TIMESTAMPTZ NOT NULL`。
- `idempotency_key VARCHAR(100) NOT NULL`，`reversal_of UUID NULL`（指向被冲正的原始流水 id）。
- 索引：`(household_id, lot_id)`、`(household_id, item_id, created_at)`、`(household_id, type, created_at)`、`(household_id, location_id)`、`(idempotency_key)`。
- 流水表无任何 `UPDATE`/`DELETE` 路径；服务层只 `INSERT`，冲正只插入新行。无数据库触发器，可审计性由服务层与只读校验保证（一致性检查会对比）。

`inventory_stocktake`（盘点单）：
- `id UUID PK`，`household_id UUID NOT NULL`，`status VARCHAR(20) NOT NULL` ∈ `{DRAFT, COMPLETED, CANCELLED}`。
- `created_by UUID NOT NULL`，`created_at`、`updated_at`、`completed_at TIMESTAMPTZ NULL`。
- `version INTEGER NOT NULL DEFAULT 0`（草稿编辑乐观锁）。

`inventory_stocktake_item`（盘点条目）：
- `id UUID PK`，`stocktake_id UUID NOT NULL`，`lot_id UUID NOT NULL`，`location_id UUID NOT NULL`。
- `book_quantity NUMERIC(20,6) NOT NULL`、`actual_quantity NUMERIC(20,6) NOT NULL`、`position_revision BIGINT NOT NULL`（草稿创建时快照对应库存位 revision）。
- `reason VARCHAR(120) NULL`（差异条目确认时必填）。
- 唯一约束：`UNIQUE (stocktake_id, lot_id, location_id)`。

`inventory_idempotency_record`（幂等结果登记）：
- `id UUID PK`，`household_id UUID NOT NULL`，`idempotency_key VARCHAR(100) NOT NULL`。
- `request_hash VARCHAR(120) NOT NULL`（对命令关键字段稳定哈希），`movement_id UUID`（若已产出流水），`response_payload JSONB`（首次结果摘要）。
- `created_at TIMESTAMPTZ NOT NULL`。
- `UNIQUE (household_id, idempotency_key)`。失败回滚不留记录（事务原子）。

### 5.3 数量与精度

- `quantity`/精度由 `CatalogApi.UnitInfo.decimalScale` 决定。命令处理前服务层按 `BigDecimal.setScale(scale, RoundingMode.UNNECESSARY)` 校验，越界抛 `INVENTORY_QUANTITY_PRECISION_INVALID`。
-`quantity > 0`；`REVERSAL` 与 `ADJUSTMENT` 允许负向语义（用符号或端点表达），但任何更新后库存位 `quantity >= 0`，否则全事务回滚。

### 5.4 归档物品的处理

- 新建批次：`CatalogApi.requireActiveItem`，归档物品拒绝 → `INVENTORY_ARCHIVED_ITEM`。
- 补充现有批次（入库到已有批次）：同样要求物品 `ACTIVE`。
- 领用/报损/移位/盘点/冲正：使用 `requireItem`，允许对归档物品继续操作其历史库存。

## 6. 各库存工作流

### 6.1 入库（新建批次）

1. 校验物品 `ACTIVE`（`requireActiveItem`）。
2. 校验单位精度；耐用品初始数量默认 1，可填序列号；消耗品数量按单位精度。
3. 同一物品下序列号重复只警告，不阻止。
4. 在事务内：按 UUID 排序锁定相关记录 → 创建 `Lot`（`item_id` 此后不可改）→ `SELECT ... FOR UPDATE` 锁定/创建目标 `StockPosition` → 写 `INBOUND` 流水（`to_location_id` 为目标）→ 更新库存位数量与 `revision` → 登记 `IdempotencyRecord` 与审计 → 发布 `StockChangedEvent`。
5. `LocationApi.markReferenced` 标记目标位置已被引用（首次存在库存位后即不可删）。

### 6.2 补充现有批次（入库到已有批次）

1. 校验物品 `ACTIVE`。
2. 锁定批次（UUID 排序）→ 锁定/创建目标库存位 → 写 `INBOUND` 流水 → 更新库存位。
3. 批次日期、编号、序列号、备注如需修正，单独走「批次资料修正」端点，使用 `version` 乐观锁并写审计；`item_id` 不可改。

### 6.3 领用

1. 列表端点按 `(expiry_date ASC NULLS LAST, lot_id)` 推荐批次及可领用库存位。
2. 用户客户端最终明确选择批次与位置及数量。
3. 锁定批次和库存位 → 校验 `quantity <= stock_position.quantity`（不够抛 `INVENTORY_INSUFFICIENT_STOCK`）→ 写 `CONSUME` 流水（`from_location_id`）→ 扣减库存位、`revision++`。
4. 归档物品仍可领用（使用 `requireItem`）。

### 6.4 报损

1. 原因（`reason`）必填，校验非空。备注可选。
2. 流程同领用，流水类型为 `LOSS`。

### 6.5 移位

1. 来源位置与目标位置不能相同（否则冲突错误）。
2. 事务内：按 `(lot_id, from_location_id, to_location_id)` 的 UUID 排序统一加锁；来源 `SELECT ... FOR UPDATE` 扣减、目标 `SELECT ... FOR UPDATE` 加增；两条库存位同事务更新，只写一条 `TRANSFER` 流水。
3. 来源不足抛 `INVENTORY_INSUFFICIENT_STOCK`；目标库存位若不存在则在同事务内创建。
4. 原子性：单边失败整事务回滚，不存在「只扣减不增加」结果。

### 6.6 盘点

1. Owner/Admin/Member 均可发起盘点（按位置）。
2. 草稿：创建 `DRAFT` 盘点单，快照范围内每个成员可访问的库存位的 `quantity` 与 `revision` → 生成 `inventory_stocktake_item` 行（`book_quantity`、`position_revision`）。可保存草稿、可逐项填 `actual_quantity`、可补录「已有批次但账面为零」的条目（`book_quantity = 0`）。
3. 全新批次不在盘点创建内：先走入库，再刷新盘点（草稿刷新端点重读范围快照）。
4. 确认：
   - 重新 `SELECT ... FOR UPDATE` 锁定范围内全部库存位集合，比较当前 `(lot_id, location_id, quantity, revision)` 与草稿快照集合及 `revision`；任一不一致 → `INVENTORY_STOCKTAKE_STALE`，整单不落库。
   - 差异条目（`actual != book`）`reason` 必填，缺失抛 `INVENTORY_STOCKTAKE_NOT_DRAFT`/校验错误。
   - 为每个差异生成 `ADJUSTMENT` 流水，更新库存位与 `revision`，盘点单置 `COMPLETED`、写 `completed_at`、审计。
5. 取消：`DRAFT` → `CANCELLED`，不产流水。
6. 已 `COMPLETED`/`CANCELLED` 不可再确认或编辑 → `INVENTORY_STOCKTAKE_NOT_DRAFT`。

### 6.7 冲正

1. 仅 Owner/Admin（`hasAtLeastRole(ADMIN)`）。
2. 输入 `movement_id`：加载原始流水，校验：
   - 原始流水未被冲正（查询 `reversal_of = movement_id` 的 `REVERSAL` 不存在）→ 否则 `INVENTORY_MOVEMENT_ALREADY_REVERSED`。
   - 冲正类型允许性：`INBOUND`/`CONSUME`/`LOSS`/`ADJUSTMENT`/`TRANSFER` 均可冲正；但若冲正会导致相关库存位 `quantity < 0`，则 `INVENTORY_REVERSAL_WOULD_NEGATIVE`；冲正本身写一条 `REVERSAL` 流水（含端点）并按端点对库存位做等量反向调整。
   - 状态冲突（如目标已不可冲正）→ `INVENTORY_REVERSAL_NOT_ALLOWED`。
3. 原子提交新 `REVERSAL` 流水（`reversal_of` 指向原流水）+ 库存位调整 + 幂等记录 + 审计 + `StockChangedEvent`。
4. 原始流水永不被修改或删除。

### 6.8 一致性检查

1. 仅 Owner/Admin。
2. 端点遍历指定范围（家庭/物品/全部）库存位，按流水汇总重算应有数量，与库存位比对，返回差异清单；整个过程只读，不写库存、不写流水。
3. 发现差异只报告，由人工决定后续冲正或盘点；阶段四不做自动修复。

## 7. 事务、锁和幂等

### 7.1 锁顺序与死锁规避

- 每个库存命令在单个 `@Transactional`（默认可重复读以下的业务隔离；行锁保证正确性）内完成。
- 先按相关批次的 UUID 升序固定顺序锁定；再按 UUID 排序对相关库存位逐一 `SELECT ... FOR UPDATE`。
- 该顺序覆盖目标库存位尚不存在的并发创建（先锁批次后锁位），并使任意两命令对相同集合以相同顺序加锁，降低死锁。
- 移位两端按 `(lot_id, from_location_id, to_location_id)` 的 UUID 排序统一加锁，先扣来源再加目标。
- 不使用 `OptimisticLockerInnerInterceptor` 处理库存位数量；`revision` 为业务自增（每次更新 `revision = revision + 1`），用于盘点快照比对，不作为并发扣减依据（并发由行锁保证）。

### 7.2 幂等

- 写入命令请求头/体含 `Idempotency-Key`（UUID）。服务端：
  1. 在命令事务内 `SELECT ... FOR UPDATE` 锁定 `(household_id, idempotency_key)` 行（不存在则插入占位由唯一约束争抢）。
  2. 计算 `request_hash`：对命令类型与关键字段（物品、批次、位置、数量、类型、原因等业务字段，不含请求元数据）稳定哈希。
  3. 已有记录且 `request_hash` 相同：直接返回首次 `response_payload`，不产生新流水。
  4. 已有记录且 `request_hash` 不同：返回 `409 INVENTORY_IDEMPOTENCY_CONFLICT`。
  5. 无记录：执行命令，成功时同事务写入 `IdempotencyRecord`（含 `movement_id`、摘要）并提交；失败回滚则不留幂等记录。
- 客户端规则：网络重试复用同一 `Idempotency-Key`；用户改变业务内容后生成新键。

### 7.3 事件发布

- `StockChangedEvent` 在命令事务内通过 Spring Modulith 事件登记（事务内入登记表），事务提交后由可靠投递器异步发布；带 `eventId` 供阶段五消费者去重。
- 投递失败由登记机制重试，不阻塞库存业务返回。
- 阶段四不实现 `reminder` 消费者，但提供订阅契约（事件类型公开 + 唯一 ID）。

## 8. API、权限、审计、公开事件和错误码

### 8.1 REST 端点（均位于 `/api/v1`，CSRF 保护，会话绑定当前家庭）

只读：
- `GET /inventory/stock-positions`：当前库存列表（物品摘要、批次、位置路径、数量、单位、到期日、更新时间），支持物品/位置/到期筛选与分页。
- `GET /inventory/lots`：批次列表（跨位置总量汇总），支持物品/到期/序列号/编号筛选；抽屉展示位置分布、批次资料、相关流水。
- `GET /inventory/lots/{lotId}`：批次详情（分布、资料、流水）。
- `GET /inventory/movements`：流水列表（只读），支持类型、时间、物品、位置、操作者筛选与分页；详情含影响和冲正关系。
- `GET /inventory/stocktakes`：盘点单列表（草稿/已完成/已取消）。
- `GET /inventory/consistency-report`（Owner/Admin）：一致性检查结果，只读。

写入（均要求 `Idempotency-Key`）：
- `POST /inventory/lots`：新建批次并入库。
- `POST /inventory/inbound`：补充现有批次入库。
- `POST /inventory/consume`：领用。
- `POST /inventory/loss`：报损。
- `POST /inventory/transfer`：移位。
- `POST /inventory/stocktakes`：创建盘点草稿。
- `PUT /inventory/stocktakes/{id}`：更新盘点草稿（条目、补录、刷新快照）。
- `POST /inventory/stocktakes/{id}/confirm`：确认盘点。
- `POST /inventory/stocktakes/{id}/cancel`：取消盘点草稿。
- `POST /inventory/movements/{id}/reverse`（Owner/Admin）：冲正。
- `PUT /inventory/lots/{id}`：修正批次日期/编号/序列号/备注（带 `version`，`item_id` 不可改）。

### 8.2 权限矩阵

| 端点 | Owner | Admin | Member |
|---|---:|---:|---:|
| 所有只读端点 | 是 | 是 | 是 |
| 写入命令（入库/领用/报损/移位/盘点） | 是 | 是 | 是 |
| 批次资料修正 | 是 | 是 | 是 |
| 冲正 | 是 | 是 | 否 |
| 一致性检查 | 是 | 是 | 否 |

后端用 `HouseholdApi.hasAtLeastRole(ADMIN)` 校验冲正与一致性检查；前端隐藏按钮仅为体验。直接 API 调用（绕过 UI）也必须被拒绝——由后端鉴权测试覆盖。

### 8.3 审计

- 关键审计动作：入库、领用、报损、移位、盘点确认、冲正、一致性检查、批次资料修正。通过 `SystemApi.recordAudit(...)` 在命令同事务写入 `audit_log`，`action` 稳定（如 `INVENTORY_INBOUND`），`detail` 用 JSONB 记录物品/批次/位置/数量/原因/幂等键（不含密码、会话、敏感配置）。
- 冲正审计 `action=INVENTORY_REVERSAL`，`detail.reversalOf` 指向原流水。

### 8.4 稳定错误码

均以 Problem Details 返回，含 `errorCode`、`title`、`requestId`、字段错误：

| 错误码 | HTTP | 触发 |
|---|---:|---|
| `INVENTORY_INSUFFICIENT_STOCK` | 409 | 领用/报损/移位来源不足 |
| `INVENTORY_QUANTITY_PRECISION_INVALID` | 422 | 数量精度不符合单位 `decimalScale` |
| `INVENTORY_IDEMPOTENCY_CONFLICT` | 409 | 同幂等键不同请求 |
| `INVENTORY_STOCKTAKE_STALE` | 409 | 盘点范围内库存位或 revision 已变 |
| `INVENTORY_MOVEMENT_ALREADY_REVERSED` | 409 | 目标流水已冲正 |
| `INVENTORY_REVERSAL_NOT_ALLOWED` | 409 | 目标流水类型/状态不允许冲正 |
| `INVENTORY_REVERSAL_WOULD_NEGATIVE` | 409 | 冲正会导致库存位负数 |
| `INVENTORY_ARCHIVED_ITEM` | 409 | 对归档物品新建批次或入库 |
| `INVENTORY_LOT_VERSION_CONFLICT` | 409 | 批次资料修正版本冲突 |
| `INVENTORY_STOCKTAKE_NOT_DRAFT` | 409 | 对非草稿盘点单确认/编辑 |

被不存在的物品/位置/批次引用复用既有 catalog/location 的 404/409 错误码；权限不足复用身份层的 403；字段校验缺失复用 400。

### 8.5 公开事件

- `StockChangedEvent`（见 §4.3）。事务提交后投递；`eventId` 全局唯一用于消费者去重。阶段四无订阅者，仅验证投递与去重契约。

## 9. 桌面端交互

### 9.1 信息架构

- 启用侧边栏现有「库存管理」入口 → `/inventory`。
- 页面内四个页签：当前库存、批次、流水、盘点。页头以「入库」为主操作，「领用」「报损」「移位」为快捷操作。
- AppShell 顶栏新增「库存操作」下拉入口（入库/领用/报损/移位/盘点）。
- 物品详情新增总库存、批次数和「入库」入口；位置详情替换「库存将在阶段四启用」，显示真实库存摘要、查看库存和发起盘点入口。
- 使用 Element Plus 分步对话框和现有「松间账册」视觉体系；数量不在表格中直接编辑，必须通过产生流水的业务动作改变；危险操作二次确认并说明影响范围。

### 9.2 各页签

- 当前库存：按库存位展示物品、批次、位置路径、数量、单位、到期日和更新时间；筛选 + 分页 + 右侧抽屉查看分布与最近流水。
- 批次：展示跨位置总量；抽屉展示位置分布、批次资料和相关流水；支持批次资料修正（带版本号）与序列号重复警告展示。
- 流水：只读，支持类型、时间、物品、位置和操作者筛选；详情展示影响和冲正关系；Owner/Admin 可见冲正入口。
- 盘点：展示草稿、已完成、已取消盘点单；按位置发起、保存草稿、补录账面为零的已有批次、确认（差异条目原因必填）、取消。

### 9.3 幂等与重试

- 网络重试复用同一 `Idempotency-Key`；用户改变业务内容后客户端生成新键。
- 服务端返回的最新库存立即展示，不等待异步投影。
- 友好提示：余额不足、盘点过期、版本冲突、归档物品入库分别对应可操作文案。
- 服务端数据不进入长期 Pinia 缓存；Pinia 仅存 UI 偏好与会话。

## 10. 测试策略、验收门槛和恢复约束

### 10.1 后端

- 领域单元测试：覆盖日期、精度、流水影响（来源/目标/数量/类型映射）、负库存拒绝、归档物品（新建批次拒绝、历史操作允许）、批次资料修正版本冲突、盘点快照比对、冲正的等量反向与已冲正拒绝。
- Testcontainers 集成测试：Flyway V11+ 迁移、唯一/非负/类型约束、不可变保护（流水表无更新路径测试）、自定义 XML SQL（行锁、条件更新、聚合）、真实行锁下的并发。
- 并发：两个线程同时超支同一库存位 → 恰一个成功、另一个 `INVENTORY_INSUFFICIENT_STOCK`，无负库存，无重复扣减；移位单边不可提交（来源不足全回滚）；相同幂等请求只产生一条流水。
- 盘点：范围发生任一库存变化时整单确认失败（`INVENTORY_STOCKTAKE_STALE`）。
- 一致性检查：注入差异（人为改库存位或流水）能被发现且不修改库存。
- Web：CSRF、跨家庭隔离（A 家庭不可见/操作 B 家庭库存）、OpenAPI 生成包含 inventory 端点、权限矩阵（Member 直接 API 调用冲正/一致检查返回 403）。
- 模块：`ModularityTests` 通过；无任何模块引用 `inventory.internal`；`inventory` 不得反向被 catalog/location 依赖。

### 10.2 前端与 E2E

- Vitest + Vue Test Utils 覆盖四页签渲染、筛选、分页、详情抽屉、分步对话框、错误展示（余额不足/盘点过期/版本冲突/幂等冲突/归档物品）。
- Playwright 覆盖：新建批次入库、补充现有批次、领用、报损、移位、盘点草稿/确认/取消、冲正、一致性检查；Member 不能冲正或检查一致性且直接 API 调用被拒；物品与位置详情可进入预填充库存操作。
- 键盘操作、焦点顺序与非颜色状态表达抽查。

### 10.3 最终门槛

- 所有数量可由不可变流水解释和重建（一致性检查 + 重建测试）。
- 并发操作不造成负库存或重复扣减。
- 移位、盘点和冲正保持原子性。
- 完整桌面库存主链路端到端可用。
- `make verify`、`make backend-test`、`make frontend-test`、`make frontend-build`、Playwright、`make compose-smoke`、`make e2e-smoke`、`ModularityTests`、`git diff --check` 全部通过。

### 10.4 恢复约束

- 库存位与流水均为前向迁移建立的表，恢复到空环境由 Flyway V11+ 重建；阶段四不改变封面文件存储与备份策略。
- 恢复流程仍按规格 §11.3：先 PostgreSQL、再图片目录、最后一致性检查；阶段四的一致性检查端点即为该流程的核验步骤。
- 冲正不删流水，备份/恢复不破坏流水不可变性。

## 11. 自查

- 范围：仅阶段四事实，未引入告警/报表/CSV/搜索/多单位换算/移动端/附件。
- 一致性：权限矩阵与规格 §4.2 一致；错误码与「建议稳定错误码」一一对齐；锁定策略、幂等、盘点快照、冲正、归档物品处理均与规格及已确认决策一致。
- 无占位符、无 TODO/TBD、无矛盾项；模块方向与现有 `CatalogApi`/`LocationApi`/`HouseholdApi`/`SystemApi` 公开契约相符。
- 仅修改/新增本 spec 文件，不触动源码。
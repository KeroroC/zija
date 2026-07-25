# 阶段四 4b：盘点后端 + 桌面库存主链路 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已交付的 4a 后端核心（批次/库存位/流水/入库/领用/报损/移位/冲正/一致性检查 + `/api/v1/inventory/**` 端点）之上，补齐盘点草稿/确认/取消后端工作流，并交付桌面端 `/inventory` 四页签与分步对话框，形成端到端可用的库存主链路。

**Architecture:** 后端新增 V12 迁移与 `StocktakeService`，复用 4a 的 `IdempotencyService`/`StockCommandService` 的事件与审计通路。前端新增 `inventory` API 模块、`/inventory` 路由、`InventoryPage.vue`（四页签）+ 五个分步对话框组件，并在 `AppShell` 启用侧边栏「库存管理」入口与顶栏「库存操作」下拉。沿用「松间账册」视觉体系（`tokens.css` 变量与 `el-drawer`/`el-dialog` 既有模式）。

**Tech Stack:** Vue 3、TypeScript、Vite 7、Element Plus、Pinia 3、Vitest、@vue/test-utils、Playwright；Java 25、Spring Boot 4.1.x、MyBatis-Plus、Flyway、PostgreSQL 17、Testcontainers。

**覆盖 spec：** `docs/superpowers/specs/2026-07-25-phase4-inventory-design.md` §6.6 盘点、§9 桌面端交互、§10.2 前端与 E2E。
**依赖 4a 已交付：** `InventoryService`、`StockCommandService`、`LotService`、`IdempotencyService`、`MovementMapper`、`StockPositionMapper`、`InventoryExceptionHandler`、四个库存工作流端点、`ModularityTests.inventoryModuleExistsAndDependenciesAreValid`。

---

## 计划范围

**4b 包含：**
- 后端盘点：V12 迁移（`inventory_stocktake`/`inventory_stocktake_item`）、`StocktakeService` 与四个端点（创建草稿、更新草稿、确认、取消）、Testcontainers 测试。
- 前端：`api/inventory.ts` + `types/inventory.ts`、`/inventory` 路由激活、`AppShell` 侧栏启用与顶栏「库存操作」下拉、`InventoryPage.vue` 四页签、五对话框组件、`ItemsPage`/`LocationsPage` 详情库存入口、Vitest。
- E2E：Playwright 覆盖新建批次入库、补充批次、领用、报损、移位、盘点草稿/确认/取消、冲正与一致性检查的桌面主链路。

**4b 不含：** 临期/低库存任务与通知（阶段五）；报表/CSV/全局搜索（阶段六）；多单位换算、条码、OCR、移动端、批次附件。后端核心引擎（4a）不再改动，除新增盘点表与服务。

## 前置条件

- 4a 已合入主分支：`make backend-test`、`make backend-build`、`ModularityTests`、`git diff --check` 全绿；`/api/v1/inventory/{lots,inbound,consume,loss,transfer,movements/{id}/reverse,stock-positions,lots,lots/{id},movements,consistency-report, lots/{id}}` 可用。
- 工作树干净。
- 执行前用 `superpowers:using-git-worktrees` 建工作树（或按用户偏好直接在分支上执行）。
- **本 plan 创建文档型任务；与其他 agent 并存时只新增/修改本 plan 所列文件，不触碰 4a 已交付的库存核心引擎类。** 如与并发 agent 修改同一文件（如 `AppShell.vue`、`router/index.ts`、`ItemsPage.vue`、`LocationsPage.vue`），按"后写者负责 rebase"原则处理冲突。

## 目标文件清单

**Create（后端）：**
- `backend/src/main/resources/db/migration/V12__create_inventory_stocktake.sql`
- `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeEntity.java`
- `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeItemEntity.java`
- `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeMapper.java`
- `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeItemMapper.java`
- `backend/src/main/resources/mapper/inventory/StocktakeMapper.xml`
- `backend/src/main/resources/mapper/inventory/StocktakeItemMapper.xml`
- `backend/src/main/java/com/zija/inventory/internal/StocktakeService.java`
- `backend/src/main/java/com/zija/inventory/internal/StocktakeStaleException.java`（已存在则跳过）
- `backend/src/main/java/com/zija/inventory/internal/StocktakeNotDraftException.java`

**Create（后端测试）：**
- `backend/src/test/java/com/zija/inventory/internal/StocktakeServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/InventoryStocktakeEndpointIntegrationTest.java`

**Modify（后端）：**
- `backend/src/main/java/com/zija/inventory/internal/InventoryController.java` — 追加四个盘点端点。
- `backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java` — 追加 `StocktakeStaleException`/`StocktakeNotDraftException` 映射。

**Create（前端）：**
- `frontend/src/types/inventory.ts`
- `frontend/src/api/inventory.ts`
- `frontend/src/views/InventoryPage.vue`（四页签容器）
- `frontend/src/views/inventory/CurrentStockTab.vue`
- `frontend/src/views/inventory/LotsTab.vue`
- `frontend/src/views/inventory/MovementsTab.vue`
- `frontend/src/views/inventory/StocktakesTab.vue`
- `frontend/src/views/inventory/InboundDialog.vue`（新建批次 + 补充批次两步）
- `frontend/src/views/inventory/ConsumeDialog.vue`
- `frontend/src/views/inventory/LossDialog.vue`
- `frontend/src/views/inventory/TransferDialog.vue`
- `frontend/src/views/inventory/StocktakeDialog.vue`（发起/编辑/确认盘点）
- `frontend/src/views/inventory/MovementDetailDrawer.vue`
- `frontend/src/views/inventory/LotDetailDrawer.vue`

**Create（前端测试）：**
- `frontend/src/api/inventory.test.ts`
- `frontend/src/views/InventoryPage.test.ts`
- `frontend/src/views/inventory/CurrentStockTab.test.ts`
- `frontend/src/views/inventory/LotsTab.test.ts`
- `frontend/src/views/inventory/MovementsTab.test.ts`
- `frontend/src/views/inventory/StocktakesTab.test.ts`
- `frontend/src/views/inventory/InboundDialog.test.ts`
- `frontend/src/views/inventory/ConsumeDialog.test.ts`
- `frontend/src/views/inventory/StocktakeDialog.test.ts`

**Modify（前端）：**
- `frontend/src/router/index.ts` — 增加 `/inventory` 路由。
- `frontend/src/router/index.test.ts` — 断言 `/inventory` 可解析。
- `frontend/src/components/AppShell.vue` — 启用侧栏「库存管理」入口（移除 `disabled`）；顶栏 `.header-right` 增加「库存操作」`el-dropdown`。
- `frontend/src/components/AppShell.test.ts` — 覆盖新下拉。
- `frontend/src/views/ItemsPage.vue` — 物品详情抽屉增加总库存、批次数与「入库」入口。
- `frontend/src/views/ItemsPage.test.ts` — 追加断言。
- `frontend/src/views/LocationsPage.vue` — 位置详情替换「库存将在阶段四启用」占位为真实库存摘要与「发起盘点」入口。
- `frontend/src/views/LocationsPage.test.ts` — 追加断言。
- `frontend/src/api/http.ts` — 新增 `postJsonWithIdempotency`/`putJsonWithIdempotency` 支持 `Idempotency-Key` 头。
- `frontend/src/api/http.test.ts` — 追加测试。
- `frontend/src/styles/index.css` — 仅在需要时追加少量与现有 token 一致的库存专用样式（如 `.inventory-tabs`、`.qty-cell`），保持「松间账册」体系。

**Create（E2E）：**
- `frontend/e2e/inventory.spec.ts`

每个任务结束一次提交（中文 body + 英文前缀）。前端写命令的 `Idempotency-Key` 由 `crypto.randomUUID()` 生成并缓存到对话框组件的 `ref`；网络重试复用同一 key，用户改变业务内容后重新生成。

---

## 任务 21：数据库迁移——盘点表

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__create_inventory_stocktake.sql`

- [ ] **步骤 1：创建 V12 迁移**

```sql
CREATE TABLE inventory_stocktake (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by   UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    version      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_stocktake_status
        CHECK (status IN ('DRAFT','COMPLETED','CANCELLED')),
    CONSTRAINT uq_inventory_stocktake_household UNIQUE (household_id, id)
);

CREATE INDEX idx_inventory_stocktake_household_status
    ON inventory_stocktake(household_id, status, created_at);

CREATE TABLE inventory_stocktake_item (
    id               UUID PRIMARY KEY,
    stocktake_id     UUID NOT NULL,
    household_id     UUID NOT NULL,
    lot_id           UUID NOT NULL,
    location_id      UUID NOT NULL,
    book_quantity    NUMERIC(20,6) NOT NULL,
    actual_quantity  NUMERIC(20,6) NOT NULL,
    position_revision BIGINT NOT NULL,
    reason           VARCHAR(120),
    CONSTRAINT uq_inventory_stocktake_item
        UNIQUE (stocktake_id, lot_id, location_id),
    CONSTRAINT ck_inventory_stocktake_actual_nonneg CHECK (actual_quantity >= 0),
    CONSTRAINT fk_inventory_stocktake_item_stocktake FOREIGN KEY (stocktake_id)
        REFERENCES inventory_stocktake(id)
);
```

- [ ] **步骤 2：验证迁移**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS（Flyway 在 Testcontainers 空库执行 V1–V12 成功）。

- [ ] **步骤 3：提交**

```bash
git add backend/src/main/resources/db/migration/V12__create_inventory_stocktake.sql
git commit -m "feat(inventory): 新增 V12 盘点草稿与条目表迁移"
```

---

## 任务 22：盘点持久化层

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeEntity.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeItemEntity.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeMapper.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StocktakeItemMapper.java`
- Create: `backend/src/main/resources/mapper/inventory/StocktakeMapper.xml`
- Create: `backend/src/main/resources/mapper/inventory/StocktakeItemMapper.xml`

- [ ] **步骤 1：StocktakeEntity**（MyBatis-Plus `@TableName`、`@Version`、字段 + getter/setter，遵循 `LotEntity` 风格）

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_stocktake")
public class StocktakeEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    @Version private Integer version;
    // 逐字段 getter/setter（略——按 LotEntity 模式补全）
}
```

- [ ] **步骤 2：StocktakeItemEntity**（`actualQuantity` 为 `BigDecimal`）

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.util.UUID;

@TableName("inventory_stocktake_item")
public class StocktakeItemEntity {
    @TableId private UUID id;
    private UUID stocktakeId;
    private UUID householdId;
    private UUID lotId;
    private UUID locationId;
    private BigDecimal bookQuantity;
    private BigDecimal actualQuantity;
    private Long positionRevision;
    private String reason;
    // 逐字段 getter/setter
}
```

- [ ] **步骤 3：StocktakeMapper 接口 + XML**

```java
@Mapper
public interface StocktakeMapper extends BaseMapper<StocktakeEntity> {
    /** 锁定整张盘点单行（事务内）。 */
    StocktakeEntity lockById(@Param("householdId") UUID householdId, @Param("id") UUID id);

    IPage<StocktakeEntity> findPage(Page<StocktakeEntity> page,
                                     @Param("householdId") UUID householdId,
                                     @Param("status") String status,
                                     @Param("orderBy") String orderBy);
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.StocktakeMapper">
    <select id="lockById" resultType="com.zija.inventory.internal.persistence.StocktakeEntity">
        SELECT id, household_id, status, created_by, created_at, updated_at, completed_at, version
        FROM inventory_stocktake
        WHERE household_id = #{householdId} AND id = #{id}
        FOR UPDATE
    </select>

    <select id="findPage" resultType="com.zija.inventory.internal.persistence.StocktakeEntity">
        SELECT id, household_id, status, created_by, created_at, updated_at, completed_at, version
        FROM inventory_stocktake
        <where>
            household_id = #{householdId}
            <if test="status != null and status != ''">AND status = #{status}</if>
        </where>
        ORDER BY ${orderBy}
    </select>
</mapper>
```

- [ ] **步骤 4：StocktakeItemMapper 接口 + XML**

```java
@Mapper
public interface StocktakeItemMapper extends BaseMapper<StocktakeItemEntity> {
    /** 锁定盘点单的全部条目（事务内，确认时与库存位快照比对）。 */
    List<StocktakeItemEntity> lockByStocktake(@Param("householdId") UUID householdId,
                                              @Param("stocktakeId") UUID stocktakeId);

    /** 删除草稿条目（刷新快照或取消时）。 */
    int deleteByStocktake(@Param("stocktakeId") UUID stocktakeId);

    /** 批量插入草稿条目。 */
    int batchInsert(@Param("items") List<StocktakeItemEntity> items);
}
```

`StocktakeItemMapper.xml` 实现 `lockByStocktake`（`SELECT ... FOR UPDATE`）、`deleteByStocktake`、`batchInsert`（`<foreach>` 批量 `VALUES`）。

- [ ] **步骤 5：编译并提交**

Run: `cd backend && ./mvnw -q compile` → SUCCESS。
```bash
git add backend/src/main/java/com/zija/inventory/internal/persistence/Stocktake*.java \
        backend/src/main/resources/mapper/inventory/Stocktake*.xml
git commit -m "feat(inventory): 新增盘点持久化层与行锁/批量插入 XML"
```

---

## 任务 23：盘点异常类与 handler 映射

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/StocktakeStaleException.java`（若 4a 未建）
- Create: `backend/src/main/java/com/zija/inventory/internal/StocktakeNotDraftException.java`
- Modify: `backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java`

- [ ] **步骤 1：异常类**

```java
package com.zija.inventory.internal;

public class StocktakeStaleException extends RuntimeException {
    public StocktakeStaleException() { super(); }
    public StocktakeStaleException(String m) { super(m); }
}
```

```java
package com.zija.inventory.internal;

public class StocktakeNotDraftException extends RuntimeException {
    public StocktakeNotDraftException() { super(); }
    public StocktakeNotDraftException(String m) { super(m); }
}
```

- [ ] **步骤 2：handler 追加映射**

在 `InventoryExceptionHandler` 追加：

```java
@ExceptionHandler(StocktakeStaleException.class)
ProblemDetail handleStocktakeStale(HttpServletRequest request) {
    return problem(request, HttpStatus.CONFLICT, "盘点范围内库存已变化", "INVENTORY_STOCKTAKE_STALE");
}

@ExceptionHandler(StocktakeNotDraftException.class)
ProblemDetail handleStocktakeNotDraft(HttpServletRequest request) {
    return problem(request, HttpStatus.CONFLICT, "盘点单不是草稿状态", "INVENTORY_STOCKTAKE_NOT_DRAFT");
}
```

- [ ] **步骤 3：编译并提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/Stocktake*Exception.java \
        backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java
git commit -m "feat(inventory): 盘点过期与非草稿异常映射"
```

---

## 任务 24：StocktakeService 创建草稿（TDD）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/StocktakeService.java`
- Test: `backend/src/test/java/com/zija/inventory/internal/StocktakeServiceIntegrationTest.java`

公开方法（package 内）：
```java
UUID createDraft(UUID householdId, UUID accountId, UUID locationId);
List<StocktakeItemEntity> draftItems(UUID householdId, UUID stocktakeId);
```

`createDraft` 流程（`@Transactional`）：
1. `LocationApi.requireLocation(householdId, locationId)`。
2. insert `inventory_stocktake`（status=DRAFT, createdBy, version=0）。
3. 查询范围内全部库存位（`StockPositionMapper.findPage` 全量或新增 `findByHouseholdLocation(householdId, locationId)`——若 4a 未提供，本任务在 `StockPositionMapper` 追加方法 `List<StockPositionEntity> lockByLocation(householdId, locationId)` 返回不带 `FOR UPDATE` 的快照集合，事务内不锁库存位——草稿不强求锁，确认再锁）。
4. 对每个库存位写一条 `inventory_stocktake_item`（`bookQuantity=当前 quantity`，`actualQuantity=bookQuantity`，`positionRevision=当前 revision`）。
5. 若范围内无库存位，仍创建空草稿（条目数量 0），前端展示「该位置无库存」。
6. 返回 stocktakeId。

测试用例：
1. 范围内 2 个库存位 → 草稿条目 2 条，`book=actual=当前 quantity`，`positionRevision` 匹配。
2. 范围内无库存位 → 草稿创建，条目 0。
3. 不存在的 location → `CatalogArchivedDictionaryException`/`LocationApi` 抛错映射（409/404，沿用 location 模块既有错误码）。

- [ ] **步骤 1：写失败测试**（Testcontainers，seed 套路同 4a 测试；`@MockitoBean LocationApi` + `when(locationApi.requireLocation(...)).thenReturn(...)`）

- [ ] **步骤 2：验证失败** — `mvnw -q -Dtest=StocktakeServiceIntegrationTest test` 编译失败。

- [ ] **步骤 3：实现 createDraft + draftItems**

需在 `StockPositionMapper` 追加（若 4a 未提供）：
```java
List<StockPositionEntity> findByLocation(@Param("householdId") UUID householdId,
                                         @Param("locationId") UUID locationId);
```
XML `SELECT ... WHERE household_id=? AND location_id=? ORDER BY lot_id`（无 FOR UPDATE）。

- [ ] **步骤 4：验证通过 + 提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/StocktakeService.java \
        backend/src/main/java/com/zija/inventory/internal/persistence/StockPositionMapper.java \
        backend/src/main/resources/mapper/inventory/StockPositionMapper.xml \
        backend/src/test/java/com/zija/inventory/internal/StocktakeServiceIntegrationTest.java
git commit -m "feat(inventory): 盘点草稿创建与位置快照"
```

---

## 任务 25：StocktakeService 更新草稿与补录

**Files:** Modify `StocktakeService.java`；追加测试。

方法：
```java
void updateDraft(UUID householdId, UUID stocktakeId, int clientVersion,
                 List<StocktakeItemUpdate> updates);
record StocktakeItemUpdate(UUID lotId, UUID locationId, BigDecimal actualQuantity, String reason) {}
```

流程（`@Transactional`）：
1. `StocktakeMapper.lockById` → 状态必须 DRAFT，否则 `StocktakeNotDraftException`。
2. MyBatis-Plus `updateById` with `@Version` → 行数 0 抛 `InventoryLotVersionConflictException`? 不语义——新增 `StocktakeVersionConflictException`? 决定：复用既有 `InventoryLotVersionConflictException` 不优雅但避免新增。**决定**：盘点草稿版本冲突复用 `INVENTORY_LOT_VERSION_CONFLICT` 错误码（语义冲突 generic），抛 `InventoryLotVersionConflictException`。
3. `StocktakeItemMapper.lockByStocktake` → 校验每条 `updates` 的 `(lotId, locationId)` 必须存在于草稿；不存在则抛 `StocktakeNotDraftException`? 不妥——补录「账面为零的已有批次」应允许添加不在草稿中的条目。**修正**：补录流程在上一步：对每条 `update`，若草稿中无该 `(lot,location)`，验证批次存在且物品对应库存位为空或 quantity=0（即「账面为零的已有批次」），再 insert 草稿条目（`bookQuantity=0`，`positionRevision=当前库存位 revision 或 0 if 不存在`）；若批次不存在或库存位有正余量，抛 `InventoryReversalNotAllowedException`? 不优雅。**最终决策**:补录只允许「库存位确实存在但 quantity=0」或「批次存在但无库存位」两种情形，否则抛 `StocktakeNotDraftException`（复用 409）。实现：补录前 `StockPositionMapper.lockOne(hh, lot, location)`；存在则 `quantity==0` 才补录（`bookQuantity=0`，`positionRevision=revision`）；不存在则 `bookQuantity=0`，`positionRevision=0`，并需校验 `LotService.requireLot(hh, lot)` 存在。违反抛 `StocktakeNotDraftException`。
4. 更新既有条目 `actualQuantity` 与 `reason`（差异条目 `reason` 留待确认时强制，此处可空）。
5. 全新批次不在草稿补录范围内（spec §6.6：先走入库再刷新盘点）。

测试：
1. 更新 `actualQuantity` → 草稿条目 actual 改变、book 不变。
2. 补录账面为零批次 → 草稿条目 +1，book=0。
3. 补录有正余量库存位 → `StocktakeNotDraftException`。
4. 补录不存在的批次 → `StocktakeNotDraftException`。
5. 非 DRAFT 状态更新 → `StocktakeNotDraftException`。

- [ ] TDD 循环 + 提交 `feat(inventory): 盘点草稿更新与账面为零补录`。

---

## 任务 26：StocktakeService 刷新草稿快照

**Files:** Modify `StocktakeService`；追加测试。

方法：
```java
void refreshDraft(UUID householdId, UUID stocktakeId, int clientVersion);
```

流程：草稿 DRAFT → 删除全部 `inventory_stocktake_item`（`StocktakeItemMapper.deleteByStocktake`）→ 用当前库存位快照重新批量插入（同 `createDraft` 步骤 3–4）。用于「全新批次先入库再刷新盘点」场景。版本号 bump 由 `updateById` 触发。

测试：刷新后条目重新快照当前 quantity/revision；非 DRAFT 拒绝。

- [ ] TDD + 提交 `feat(inventory): 盘点草稿刷新快照`。

---

## 任务 27：StocktakeService 确认盘点（TDD，核心原子性）

**Files:** Modify `StocktakeService`；追加测试。

方法：
```java
ConfirmResult confirm(UUID householdId, UUID stocktakeId, int clientVersion);
record ConfirmResult(UUID stocktakeId, int adjustedCount) {}
```

流程（`@Transactional`）：
1. `StocktakeMapper.lockById` → DRAFT 否则 `StocktakeNotDraftException`；`updateById` 带版本校验。
2. `StocktakeItemMapper.lockByStocktake` 取草稿全部条目集合 S。
3. 对每个条目 `(lotId, locationId)`：`StockPositionMapper.lockOne`（事务内行锁）。比对：当前库存位不存在但草稿 `bookQuantity > 0` → `StocktakeStaleException`；存在但 `quantity != bookQuantity` 或 `revision != positionRevision` → `StocktakeStaleException`。整事务回滚，不写任何流水。
4. 差异条目（`actual != book`）：`reason` 非空校验 → 空则抛 `StocktakeNotDraftException`? 不语义。**决定**：差异条目 reason 缺失抛 `StocktakeNotDraftException` 不语义；新增轻量做法——抛 `IllegalArgumentException("差异条目原因必填")` 由全局兜底为 400 VALIDATION_FAILED。**最终**：服务层抛 `InventoryQuantityPrecisionInvalidException`? 不。直接抛 `StocktakeNotDraftException` 复用。**审查决定**：新增错误码 `INVENTORY_STOCKTAKE_DIFF_REASON_REQUIRED`？spec §8.4 清单不含此码——避免范围膨胀，复用 `VALIDATION_FAILED`（400）。实现抛 `IllegalArgumentException`。
5. 为每个差异生成 `ADJUSTMENT` 流水：
   - `actual > book`：正调整，`to_location_id=location`，`quantity=actual-book`，`reason`。
   - `actual < book`：负调整，`from_location_id=location`，`quantity=book-actual`，`reason`。
   - 调用 `StockPositionMapper.addQuantity` / `subtractIfSufficient`（后者：若 `book-actual > 当前 quantity` 在锁后不可能发生——但若并发另一盘点已在另一事务改库存位？步骤 3 的 `lockOne FOR UPDATE` 已确保整盘期间不被改）。
   - 流水 `idempotency_key = "STOCKTAKE:"+stocktakeId+":"+lotId+":"+locationId`（同一盘点重新确认不会发生——确认后状态非 DRAFT；key 仅供幂等表使用）。
6. 盘点单 status → `COMPLETED`，`completed_at=now`，version bump。
7. `SystemApi.recordAudit(action="INVENTORY_STOCKTAKE_CONFIRM")` + 每条 ADJUSTMENT 一个 `StockChangedEvent`。
8. 返回 `adjustedCount`。

测试（核心）：
1. 无差异确认 → status COMPLETED，无流水，adjustedCount=0。
2. 有差异确认 → 差异条目各产一条 ADJUSTMENT，库存位 quantity 更新，revision+1。
3. **范围发生任一库存变化时整单确认失败**：草稿创建后用 `StockCommandService.consume` 改变范围内某库存位 → 再 confirm 抛 `StocktakeStaleException`，无任何流水、status 仍 DRAFT。
4. 差异条目 reason 缺失 → 400 `VALIDATION_FAILED`，无流水。
5. 已 COMPLETED 盘点 confirm → `StocktakeNotDraftException`。
6. 版本冲突 → `InventoryLotVersionConflictException`。

- [ ] TDD 循环 + 提交 `feat(inventory): 盘点确认原子性与过期/差异原因拒绝`。

---

## 任务 28：StocktakeService 取消草稿

**Files:** Modify `StocktakeService`；追加测试。

方法 `cancel(householdId, stocktakeId, clientVersion)`：状态 DRAFT → CANCELLED，version bump，`StocktakeItemMapper.deleteByStocktake` 清理条目，无流水无事件，写审计 `INVENTORY_STOCKTAKE_CANCEL`。非 DRAFT 拒绝。

- [ ] TDD + 提交 `feat(inventory): 盘点草稿取消`。

---

## 任务 29：InventoryController 盘点端点与 MockMvc 测试

**Files:**
- Modify: `backend/src/main/java/com/zija/inventory/internal/InventoryController.java`
- Test: `backend/src/test/java/com/zija/inventory/internal/InventoryStocktakeEndpointIntegrationTest.java`

端点：
- `POST /inventory/stocktakes`（body `{locationId}`，无需 Idempotency-Key 草稿创建幂等可由键保护：本任务加 `Idempotency-Key` 头可选支持，与 4a 幂等服务集成）。
- `PUT /inventory/stocktakes/{id}`（body `{version, updates:[{lotId,locationId,actualQuantity,reason}]}`）。
- `PUT /inventory/stocktakes/{id}/refresh`（body `{version}`）。
- `POST /inventory/stocktakes/{id}/confirm`（body `{version}`）。
- `POST /inventory/stocktakes/{id}/cancel`（body `{version}`）。
- `GET /inventory/stocktakes`（`?status=&page=&pageSize=`）。
- `GET /inventory/stocktakes/{id}`（详情含条目）。

权限：所有活跃成员可创建/更新/确认/取消（spec §8.2）。

测试：覆盖创建、更新、刷新、确认成功、确认过期、理由缺失、取消、列表与详情、跨家庭隔离、Member 可操作。部分测试复用 `StocktakeServiceIntegrationTest` 不重复；本任务聚焦 HTTP 层与 Problem Details 映射。

- [ ] TDD + 提交 `feat(inventory): 盘点 REST 端点与跨家庭/状态/校验测试`。

---

## 任务 30：前端 http 扩展 Idempotency-Key 支持

**Files:**
- Modify: `frontend/src/api/http.ts`
- Test: `frontend/src/api/http.test.ts`

- [ ] **步骤 1：写失败测试**

```ts
import { describe, it, expect, vi, beforeEach } from "vitest";

describe("postJsonWithIdempotency", () => {
  beforeEach(() => { vi.unstubAllGlobals(); });

  it("sends Idempotency-Key header", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const { postJsonWithIdempotency } = await import("./http");
    await postJsonWithIdempotency("/api/v1/x", { a: 1 }, "key-123");
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string,string>)["Idempotency-Key"]).toBe("key-123");
    expect((init.headers as Record<string,string>)["Content-Type"]).toBe("application/json");
  });
});
```

> 测试还需先 mock `ensureCsrf`/`getCookie` 使其不发起真实 CSRF 调用——参照既有 `http.test.ts` 的 stub 模式（先 read 现有测试确认 stub 套路，再复用）。

- [ ] **步骤 2：验证失败** — `npm --prefix frontend test -- --run http` 失败。

- [ ] **步骤 3：在 http.ts 实现**

```ts
export async function postJsonWithIdempotency<T>(
  path: string,
  body: unknown,
  idempotencyKey: string
): Promise<T> {
  return requestWithHeaders<T>("POST", path, body, { "Idempotency-Key": idempotencyKey });
}

export async function putJsonWithIdempotency<T>(
  path: string,
  body: unknown,
  idempotencyKey: string
): Promise<T> {
  return requestWithHeaders<T>("PUT", path, body, { "Idempotency-Key": idempotencyKey });
}

async function requestWithHeaders<T>(
  method: string,
  path: string,
  body: unknown,
  extra: Record<string, string>
): Promise<T> {
  if (method !== "GET") await ensureCsrf();
  const headers: Record<string, string> = { Accept: "application/json", ...extra };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const cookieToken = getCookie("XSRF-TOKEN");
  if (cookieToken && method !== "GET") headers["X-XSRF-TOKEN"] = cookieToken;
  // 复用既有 request<T> 主体逻辑（提取一份内部实现，或在 request<T> 上增加可选 extra headers 参数）
  // 注意：现有 request<T> 不接受 extra headers，本任务将其重构以接受可选 extra 参数且默认空对象，
  // 既有 postJson/putJson 保持签名不变（传 undefined extra）。
  const response = await fetch(baseUrl() + path, {
    method,
    credentials: "same-origin",
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  // 后续处理与 request<T> 完全一致——重构为共享 helper 避免重复。
  // 实现时把 request<T> 现有逻辑抽取为 `coreRequest<T>(method, path, body, extra)`
  // 既有 postJson/putJson/deleteJson 调用 coreRequest(..., undefined)。
  // 这里调用 coreRequest(method, path, body, extra)。
  return coreReturn<T>(response); // 实现时调用重构后的 coreRequest 并 return 其结果
}
```

> 实现者指引：把现有 `request<T>(method, path, body)` 改名为 `coreRequest<T>(method, path, body, extra?)`，`extra` 默认 `{}`，合并进 headers；`postJson/putJson/deleteJson` 调用 `coreRequest` 不传 extra；新增 `postJsonWithIdempotency/putJsonWithIdempotency` 传 `{ "Idempotency-Key": key }`。保持既有 `postJsonAndRefreshCsrf` 行为不变（仍调用 coreRequest 并 `clearCsrf`）。

- [ ] **步骤 4：验证通过** — `npm --prefix frontend test -- --run http.test` 全绿（既有用例无回归）。

- [ ] **步骤 5：提交**

```bash
git add frontend/src/api/http.ts frontend/src/api/http.test.ts
git commit -m "feat(frontend): http 扩展 Idempotency-Key 自定义头支持"
```

---

## 任务 31：前端 inventory 类型与 API 模块

**Files:**
- Create: `frontend/src/types/inventory.ts`
- Create: `frontend/src/api/inventory.ts`
- Test: `frontend/src/api/inventory.test.ts`

- [ ] **步骤 1：types/inventory.ts**

```ts
export interface StockPosition {
  lotId: string;
  locationId: string;
  itemName: string;
  itemManagementType: "CONSUMABLE" | "DURABLE";
  unitName: string;
  quantity: string;
  revision: number;
  expiryDate: string | null;
  lotNumber: string | null;
  serialNumber: string | null;
  updatedAt: string;
}

export interface StockPositionListResponse { items: StockPosition[]; total: number; page: number; pageSize: number; }

export interface LotSummary {
  lotId: string;
  itemId: string;
  itemName: string;
  unitName: string;
  totalQuantity: string;
  expiryDate: string | null;
  lotNumber: string | null;
  serialNumber: string | null;
  positions: { locationId: string; locationName: string; quantity: string; revision: number }[];
  version: number;
}

export interface LotListResponse { items: LotSummary[]; total: number; page: number; pageSize: number; }

export interface Movement {
  id: string;
  lotId: string;
  itemId: string;
  itemName: string;
  type: "INBOUND" | "CONSUME" | "LOSS" | "ADJUSTMENT" | "TRANSFER" | "REVERSAL";
  quantity: string;
  unitName: string;
  fromLocationId: string | null;
  fromLocationName: string | null;
  toLocationId: string | null;
  toLocationName: string | null;
  reason: string | null;
  memo: string | null;
  operatorUsername: string | null;
  businessTime: string;
  createdAt: string;
  idempotencyKey: string;
  reversalOf: string | null;
  reversedBy: string | null;
}

export interface MovementListResponse { items: Movement[]; total: number; page: number; pageSize: number; }

export interface StocktakeSummary { id: string; status: "DRAFT" | "COMPLETED" | "CANCELLED"; createdBy: string; createdAt: string; completedAt: string | null; version: number; }
export interface StocktakeListResponse { items: StocktakeSummary[]; total: number; page: number; pageSize: number; }
export interface StocktakeItem { lotId: string; locationId: string; bookQuantity: string; actualQuantity: string; reason: string | null; }
export interface StocktakeDetail extends StocktakeSummary { items: StocktakeItem[]; }

export interface InboundResult { lotId: string; locationId: string; movementId: string; quantityAfter: string; serialDuplicated: boolean; }
export interface ConsistencyDiscrepancy { lotId: string; locationId: string; expected: string; actual: string; }
```

> 注：`itemName/unitName/locationName/operatorUsername/reversedBy` 等冗余字段需 4a 后端列表端点 JOIN `catalog_item`/`catalog_unit`/`location_location`/`account` 返回。若 4a 端点未返回名称，本 4b 任务在 4a 的 `findPage` XML 追加 JOIN 与列（只读查询不破坏 4a 引擎），或在前端按 id 查名拼接。**决策**：为避免与 4a 重叠，前端在 `api/inventory.ts` 内按需调用 `fetchItems`/`fetchLocations`/`fetchCategories` 等既有 API 构建本地 `Map<id,name>`（与 `ItemsPage` 的 `categoryMap`/`brandMap` 模式一致）。后端列表只返回 ID，名称在前端 join。据此 `types/inventory.ts` 上述字段保留但可选（运行时由前端组装）。

- [ ] **步骤 2：api/inventory.ts**

```ts
import { getJson, putJson, postJsonWithIdempotency, putJsonWithIdempotency, deleteJson } from "./http";
import type {
  StockPositionListResponse, LotListResponse, LotSummary,
  MovementListResponse, StocktakeListResponse, StocktakeDetail,
  InboundResult, ConsistencyDiscrepancy
} from "../types/inventory";

export async function fetchStockPositions(params: {
  itemId?: string; locationId?: string; page?: number; pageSize?: number; sort?: string;
}): Promise<StockPositionListResponse> {
  const q = new URLSearchParams();
  if (params.itemId) q.set("itemId", params.itemId);
  if (params.locationId) q.set("locationId", params.locationId);
  if (params.page) q.set("page", String(params.page));
  if (params.pageSize) q.set("pageSize", String(params.pageSize));
  if (params.sort) q.set("sort", params.sort);
  return getJson<StockPositionListResponse>(`/api/v1/inventory/stock-positions?${q}`);
}

export async function fetchLots(params: {
  itemId?: string; q?: string; page?: number; pageSize?: number; sort?: string;
}): Promise<LotListResponse> {
  const q = new URLSearchParams();
  // 复用 fetchItems 模式
  return getJson<LotListResponse>(`/api/v1/inventory/lots?${q}`);
}

export async function fetchLot(lotId: string): Promise<LotSummary> {
  return getJson<LotSummary>(`/api/v1/inventory/lots/${lotId}`);
}

export async function fetchMovements(params: {
  type?: string; itemId?: string; locationId?: string; operatorAccountId?: string;
  from?: string; to?: string; page?: number; pageSize?: number; sort?: string;
}): Promise<MovementListResponse> {
  const q = new URLSearchParams();
  // 逐项 set
  return getJson<MovementListResponse>(`/api/v1/inventory/movements?${q}`);
}

export async function fetchStocktakes(params: { status?: string; page?: number; pageSize?: number }): Promise<StocktakeListResponse> {
  const q = new URLSearchParams();
  // 逐项 set
  return getJson<StocktakeListResponse>(`/api/v1/inventory/stocktakes?${q}`);
}

export async function fetchStocktake(id: string): Promise<StocktakeDetail> {
  return getJson<StocktakeDetail>(`/api/v1/inventory/stocktakes/${id}`);
}

export async function createStocktake(locationId: string, idempotencyKey: string): Promise<{ id: string }> {
  return postJsonWithIdempotency<{ id: string }>("/api/v1/inventory/stocktakes", { locationId }, idempotencyKey);
}

export async function updateStocktakeDraft(id: string, version: number,
  updates: { lotId: string; locationId: string; actualQuantity: string; reason?: string }[]
): Promise<void> {
  return putJson(`/api/v1/inventory/stocktakes/${id}`, { version, updates });
}

export async function refreshStocktakeDraft(id: string, version: string|number, idempotencyKey: string): Promise<void> {
  return putJsonWithIdempotency(`/api/v1/inventory/stocktakes/${id}/refresh`, { version }, idempotencyKey);
}

export async function confirmStocktake(id: string, version: number, idempotencyKey: string): Promise<{ adjustedCount: number }> {
  return postJsonWithIdempotency<{ adjustedCount: number }>(`/api/v1/inventory/stocktakes/${id}/confirm`, { version }, idempotencyKey);
}

export async function cancelStocktake(id: string, version: number, idempotencyKey: string): Promise<void> {
  return postJsonWithIdempotency(`/api/v1/inventory/stocktakes/${id}/cancel`, { version }, idempotencyKey);
}

export async function inboundNewLot(cmd: {
  itemId: string; quantity: string; purchaseDate?: string; productionDate?: string;
  expiryDate?: string; lotNumber?: string; serialNumber?: string; memo?: string;
  locationId: string;
}, idempotencyKey: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>("/api/v1/inventory/lots", { ...cmd }, idempotencyKey);
}

export async function inboundExistingLot(cmd: {
  lotId: string; quantity: string; memo?: string; locationId: string;
}, idempotencyKey: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>("/api/v1/inventory/inbound", { ...cmd }, idempotencyKey);
}

export async function consumeStock(cmd: {
  lotId: string; locationId: string; quantity: string; reason?: string; memo?: string;
}, idempotencyKey: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>("/api/v1/inventory/consume", { ...cmd }, idempotencyKey);
}

export async function lossStock(cmd: {
  lotId: string; locationId: string; quantity: string; reason: string; memo?: string;
}, idempotencyKey: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>("/api/v1/inventory/loss", { ...cmd }, idempotencyKey);
}

export async function transferStock(cmd: {
  lotId: string; fromLocationId: string; toLocationId: string; quantity: string; memo?: string;
}, idempotencyKey: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>("/api/v1/inventory/transfer", { ...cmd }, idempotencyKey);
}

export async function reverseMovement(movementId: string, reason: string, memo?: string, idempotencyKey?: string): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(`/api/v1/inventory/movements/${movementId}/reverse`, { reason, memo }, idempotencyKey ?? crypto.randomUUID());
}

export async function fetchConsistencyReport(itemId?: string): Promise<ConsistencyDiscrepancy[]> {
  const q = new URLSearchParams();
  if (itemId) q.set("itemId", itemId);
  return getJson<ConsistencyDiscrepancy[]>(`/api/v1/inventory/consistency-report?${q}`);
}

export async function updateLotMeta(lotId: string, version: number, data: {
  purchaseDate?: string; productionDate?: string; expiryDate?: string;
  lotNumber?: string; serialNumber?: string; memo?: string;
}): Promise<void> {
  return putJson(`/api/v1/inventory/lots/${lotId}`, { version, ...data });
}
```

- [ ] **步骤 3：api/inventory.test.ts** — 用 `vi.mock("./http")` mock `postJsonWithIdempotency/getJson`，断言各函数拼出正确 URL/headers/params（参照既有 `http.test.ts` 模式）。

- [ ] **步骤 4：验证通过 + 提交**

```bash
npm --prefix frontend test -- --run inventory.test http.test
git add frontend/src/types/inventory.ts frontend/src/api/inventory.ts frontend/src/api/inventory.test.ts
git commit -m "feat(frontend): 库存 API 与类型模块及 Idempotency-Key 用例"
```

---

## 任务 32：路由激活与 AppShell 入口

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/index.test.ts`
- Modify: `frontend/src/components/AppShell.vue`
- Modify: `frontend/src/components/AppShell.test.ts`

- [ ] **步骤 1：路由追加**

`router/index.ts` import 增 `import InventoryPage from "../views/InventoryPage.vue";`，routes 增：
```ts
{ path: "/inventory", name: "inventory", component: InventoryPage, meta: { title: "库存管理" } },
```

`index.test.ts` 追加断言：`expect(router.resolve("/inventory").name).toBe("inventory");`

> 在 `InventoryPage.vue`（任务 33）创建后此测试才可解析；本任务先建路由 + `InventoryPage.vue` 空壳（`<template><div class="page-container"><h2 class="page-title">库存管理</h2></div></template>`），任务 33 再填充。空壳可让路由测试通过。

- [ ] **步骤 2：AppShell 启用「库存管理」侧栏** — 删除 `<el-menu-item index="/inventory" disabled>` 的 `disabled` attribute。

- [ ] **步骤 3：AppShell 顶栏「库存操作」下拉**

在 `.header-right` 容器内、`roleLabel` 之后插入：
```vue
<el-dropdown trigger="click" @command="onInventoryCommand">
  <el-button size="small" type="primary" plain>
    库存操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
  </el-button>
  <template #dropdown>
    <el-dropdown-menu>
      <el-dropdown-item command="inbound">入库</el-dropdown-item>
      <el-dropdown-item command="consume">领用</el-dropdown-item>
      <el-dropdown-item command="loss">报损</el-dropdown-item>
      <el-dropdown-item command="transfer">移位</el-dropdown-item>
      <el-dropdown-item command="stocktake" divided>发起盘点</el-dropdown-item>
      <el-dropdown-item command="consistency" v-if="isAdmin">一致性检查</el-dropdown-item>
    </el-dropdown-menu>
  </template>
</el-dropdown>
```
`<script setup>` 增 `ArrowDown` import、`isAdmin = computed(() => session.role === "OWNER" || session.role === "ADMIN")`、`onInventoryCommand(cmd)` dispatch 到 `router.push({ name: "inventory", query: { action: cmd } })`（`InventoryPage` 监听 `route.query.action` 弹对应对话框）。

- [ ] **步骤 4：AppShell.test.ts 追加** — 断言「库存操作」下拉存在、点击「入库」后 `router.push` 被调用（mock 入库 API + router）；`isAdmin=false`（Member）时「一致性检查」项不可见。

- [ ] **步骤 5：验证 + 提交**

Run: `npm --prefix frontend test -- --run AppShell.test router/index.test`
```bash
git add frontend/src/router/index.ts frontend/src/router/index.test.ts \
        frontend/src/components/AppShell.vue frontend/src/components/AppShell.test.ts \
        frontend/src/views/InventoryPage.vue
git commit -m "feat(frontend): 启用库存侧栏入口与顶栏库存操作下拉"
```

---

## 任务 33：InventoryPage 四页签容器

**Files:**
- Create: `frontend/src/views/InventoryPage.vue`（替换任务 32 空壳，import 四个子 tab）
- Create: `frontend/src/views/inventory/{CurrentStockTab,LotsTab,MovementsTab,StocktakesTab}.vue`（最小骨架，后续任务填充）
- Test: `frontend/src/views/InventoryPage.test.ts`

`InventoryPage` 结构：`<el-tabs v-model="activeTab">` + 四个 `<el-tab-pane>`，每个 `lazy`。页头「入库」主按钮 + 「领用/报损/移位」快捷按钮 + 「发起盘点」按钮（仅显示，Member 也可发起）。监听 `route.query.action` 切 tab 或弹对话框。统一管理对话框 `visible` ref 与命令 `Idempotency-Key`。

测试：四个 tab 渲染、点击主按钮打开 `InboundDialog`、`route.query.action="consume"` 打开 `ConsumeDialog`。

骨架子组件初始仅一个标题占位（任务 34–37 填充）。本任务重点是 `InventoryPage` 容器与对话框编排。

- [ ] TDD 循环 + 提交 `feat(frontend): InventoryPage 四页签容器与对话框编排`。

---

## 任务 34：CurrentStockTab 当前库存页签

**Files:**
- Create: `frontend/src/views/inventory/CurrentStockTab.vue`
- Test: `frontend/src/views/inventory/CurrentStockTab.test.ts`

内容：
- 筛选栏：物品 `el-tree-select`（复用 `fetchItems`）、位置 `el-tree-select`（复用 `fetchLocations` 树）、排序。
- `el-table`：物品名、批次号、位置路径、数量+单位、到期日、更新时间。
- 分页。
- 右侧抽屉 `CurrentStockDetailDrawer` 展示该库存位最近流水（调用 `fetchMovements({locationId})`）。

名称映射：组件内 `onMounted` 加载 `fetchItems`/`fetchLocations` 构建 `Map`，在表格渲染时以 ID 查名；不在 Pinia 缓存。

测试：mock `../api/inventory` 与 `../api/catalog`/`../api/location`，断言表格渲染数据、筛选触发 fetch、点击行打开抽屉。

- [ ] TDD + 提交 `feat(frontend): 当前库存页签筛选/表格/抽屉`。

---

## 任务 35：LotsTab 批次页签 + LotDetailDrawer

**Files:**
- Create: `frontend/src/views/inventory/LotsTab.vue`
- Create: `frontend/src/views/inventory/LotDetailDrawer.vue`
- Test: `frontend/src/views/inventory/LotsTab.test.ts`

内容：
- `el-table`：物品名、批次号、序列号、跨位置总量、到期日。
- 行点击打开 `LotDetailDrawer`：位置分布（`positions[]`）、批次资料（`expiryDate/lotNumber/serialNumber/memo/version`）、相关流水（`fetchMovements({itemId})` 过滤 lotId）。
- 批次资料修正：抽屉内编辑按钮 → `el-form`（`version` 隐藏携带、`item_id` 不可改）→ `updateLotMeta`。

测试：批次列表、抽屉展示分布、`updateLotMeta` 成功后刷新、版本冲突显示 `INVENTORY_LOT_VERSION_CONFLICT` 错误提示。

- [ ] TDD + 提交 `feat(frontend): 批次页签与详情抽屉、批次资料修正`。

---

## 任务 36：MovementsTab 流水页签 + MovementDetailDrawer

**Files:**
- Create: `frontend/src/views/inventory/MovementsTab.vue`
- Create: `frontend/src/views/inventory/MovementDetailDrawer.vue`
- Test: `frontend/src/views/inventory/MovementsTab.test.ts`

内容：
- 只读筛选：`type`（下拉 6 类）、时间范围（`el-date-picker`）、物品、位置、操作者。
- `el-table`：时间、类型（带 dot）、物品、数量+单位、来源→目标、原因、操作者。
- 行打开 `MovementDetailDrawer`：详情 + 冲正关系（`reversalOf` 指向原流水；反之查 `reversedBy`）。
- `isAdmin` 时显示「冲正」按钮 → `el-dialog`（reason + memo + Idempotency-Key）→ `reverseMovement`；成功后刷新列表。
- 错误提示：`INVENTORY_MOVEMENT_ALREADY_REVERSED` / `INVENTORY_REVERSAL_WOULD_NEGATIVE` / `INVENTORY_REVERSAL_NOT_ALLOWED` 友好文案。

测试：筛选/分页、抽屉、冲正成功、`ALREADY_REVERSED` 错误提示、Member 不显示冲正按钮（`session.role=MEMBER`）。

- [ ] TDD + 提交 `feat(frontend): 流水页签只读筛选与冲正`。

---

## 任务 37：StocktakesTab 盘点页签 + StocktakeDialog

**Files:**
- Create: `frontend/src/views/inventory/StocktakesTab.vue`
- Create: `frontend/src/views/inventory/StocktakeDialog.vue`
- Test: `frontend/src/views/inventory/StocktakesTab.test.ts`、`StocktakeDialog.test.ts`

`StocktakesTab`：
- 列表 `DRAFT/COMPLETED/CANCELLED` 盘点单（`fetchStocktakes`），`status` 筛选 + 分页。
- 「发起盘点」按钮 → `StocktakeDialog` step=0 选位置 → `createStocktake`。
- 行点击进入 `StocktakeDialog` 编辑/确认/取消。

`StocktakeDialog`（分步 `el-dialog`）：
- Step 1 选位置 + 创建 → `createStocktake(locationId, key)`。
- Step 2 列表条目：`book`、`actual`（`el-input-number` 精度按物品单位）、`reason`（差异条目在确认时校验非空）。补录按钮 → 弹「补录批次」子选择器（`fetchLots({itemId})` 过滤账面为零）→ `updateStocktakeDraft`。
- Step 3 确认预览差异，显示「将产生 N 条调整流水」，`confirm` → `confirmStocktake(id, version, key)`，`INVENTORY_STOCKTAKE_STALE` 友好提示并允许「刷新」→ `refreshStocktakeDraft`。
- 取消按钮 → `cancelStocktake`。
- `Idempotency-Key` 由 `crypto.randomUUID()` 缓存于组件 ref；创建/刷新/确认/取消各操作各持一个 key，重试复用，状态切换后重置。

测试：
1. 发起盘点选位置后创建草稿、展示条目。
2. 编辑 actualQuantity 不变 → 确认无差异、`COMPLETED`。
3. 差异条目 reason 空 → 确认报 400 `VALIDATION_FAILED`。
4. `INVENTORY_STOCKTAKE_STALE` → 显示「刷新」按钮，点击后重载快照。
5. 取消草稿 → `CANCELLED`，列表更新。

- [ ] TDD + 提交 `feat(frontend): 盘点页签与分步对话框、补录/确认/取消`。

---

## 任务 38：Inbound/Consume/Loss/Transfer 四个分步对话框

**Files:**
- Create: `frontend/src/views/inventory/{InboundDialog,ConsumeDialog,LossDialog,TransferDialog}.vue`
- Tests: 三个对应 `*.test.ts`（Loss 与 Consume 同构合并用一个 Loss/Consume 共享测试或两个文件均可——本任务建 4 个测试文件）

对话框模式（Element Plus `el-dialog` 内嵌 `el-steps`）：

`InboundDialog`（两步）：
- Step 0 选模式「新建批次 / 补充现有批次」。新建：选物品（`fetchItems` 仅 ACTIVE），填数量（精度按单位 `decimalScale`，`el-input-number :precision`）、批次资料（购入/生产/到期日、批次号、序列号、备注）、位置（`fetchLocations` 树）。补充：选物品→选批次（`fetchLots({itemId})`）→数量→位置。
- Step 1 确认预览（数量 + 单位 + 位置 + 序列号重复警告）。
- 提交 → `inboundNewLot` 或 `inboundExistingLot`；成功后发射 `done`，`InventoryPage` 刷新当前 tab。
- `INVENTORY_ARCHIVED_ITEM` 友好提示「该物品已归档，不能新增库存」。

`ConsumeDialog`：选物品→选批次位置（`fetchLots`+`stockPositionsOfItem`，按到期日升序推荐，无到期排后）→数量→原因/备注→`consumeStock`。`INVENTORY_INSUFFICIENT_STOCK` 提示「余量不足」。

`LossDialog`：选批次位置→数量→`reason`（`el-form-item` 必填校验 `required`）→备注→`lossStock`。

`TransferDialog`：选批次+来源位置→目标位置（不能与来源相同，前端 `el-form-item :rules` 自定义校验）→数量→`transferStock`。源=目标按钮禁用 + 校验提示。

每个对话框：`Idempotency-Key` 用 `crypto.randomUUID()` 缓存 `ref`，提交后失败可重试用同 key，用户改业务内容后 reset key。错误统一走 `ApiError.errorCode` switch 友好文案。

测试：mock API，断言提交拼出正确命令、`INVENTORY_INSUFFICIENT_STOCK`/`INVENTORY_ARCHIVED_ITEM` 友好提示、源目标相同 `TransferDialog` 拒绝提交、`LossDialog` reason 空禁用提交。

- [ ] TDD + 提交 `feat(frontend): 入库/领用/报损/移位分步对话框与错误友好提示`。

---

## 任务 39：ItemsPage 与 LocationsPage 库存入口

**Files:**
- Modify: `frontend/src/views/ItemsPage.vue`
- Modify: `frontend/src/views/ItemsPage.test.ts`
- Modify: `frontend/src/views/LocationsPage.vue`
- Modify: `frontend/src/views/LocationsPage.test.ts`

`ItemsPage` 详情抽屉追加：
- 总库存（`fetchStockPositions({itemId})` 聚合 quantity）、批次数。
- 「入库」按钮 → `router.push({ name: "inventory", query: { action: "inbound", itemId } })`。

`LocationsPage` 位置详情：
- 替换「库存将在阶段四启用」占位为：库存摘要（`fetchStockPositions({locationId})` 计数与总量）、「查看库存」按钮（`router.push({ name: "inventory", query: { locationId } })`）、「发起盘点」按钮（`router.push({ name: "inventory", query: { action: "stocktake", locationId } })`）。

测试：
- `ItemsPage` 详情抽屉显示总库存与批次数、入库按钮跳转。
- `LocationsPage` 位置详情不再显示「将在阶段四启用」、显示库存摘要与两个入口。

- [ ] TDD + 提交 `feat(frontend): 物品与位置详情库存摘要与入库/盘点入口`。

---

## 任务 40：Vitest 全量与前端构建

- [ ] **步骤 1：Vitest 全量**

Run: `npm --prefix frontend test -- --run`
Expected: 全绿，含新增 inventory 测试与既有测试无回归。

- [ ] **步骤 2：前端构建（含 typecheck）**

Run: `make frontend-build`
Expected: `vue-tsc` 无类型错误，`vite build` 成功。

- [ ] **步骤 3：layout/diff 检查**

Run: `git diff --check`
Expected: 无空白错误。

- [ ] **步骤 4：提交（若步骤 1–3 有任何修复）**

仅当出现修复时提交 `fix(frontend): 库存前端测试与构建对齐`；否则跳过。

---

## 任务 41：Playwright E2E

**Files:**
- Create: `frontend/e2e/inventory.spec.ts`

参考 `catalog.spec.ts` 与 `locations.spec.ts` 的 helper 用法（`ensureBootstrapped`、`loginViaUi`），并在 spec 中先用 API/直连创建物品、单位、位置、批次等基础数据（参照 `catalog.spec.ts` 的创建流程）。WriteOne spec 覆盖主链路：

```ts
import { expect, test } from "@playwright/test";
import { ensureBootstrapped, ensureCatalogBasics } from "./helpers"; // 若无 ensureCatalogBasics，spec 内联创建

test("库存主链路：入库→领用→报损→移位→盘点→冲正", async ({ page }) => {
  await ensureBootstrapped(page);
  // 1. 通过 UI 或 API 建一个物品+单位+位置（参考 catalog.spec 创建流程）
  // 2. /inventory，点「入库」对话框新建批次入库 quantity=5
  // 3. 当前库存页看到 5；批次页看到 1 个批次；流水页看到 INBOUND 一条
  // 4. 点「领用」选批次+位置 quantity=2 → 库存 3、流水 CONSUME 一条
  // 5. 点「报损」quantity=1 reason=过期 → 库存 2、LOSS 一条
  // 6. 点「移位」从位置 A 到位置 B quantity=1 → A=1 B=1、TRANSFER 一条
  // 7. 点「发起盘点」选位置 A，actual=0、reason=遗失 → 确认，LOSS/ADJUSTMENT 一条、A=0
  // 8. Owner 点流水页冲正一条 CONSUME → 库存回升、REVERSAL 一条
  // 9. Member 登录：冲正按钮不可见、直接 API 调用被后端 403（用 page.request.post 验证）
  // 10. 一致性检查（Owner）：无差异或差异报告展示
});
```

- [ ] **步骤 1：编写 E2E 用例**（多步，Playwright 选择器用既有 `.el-dialog`+文本模式与 `getByRole`/`getByPlaceholder`，参照 `catalog.spec.ts`）。
- [ ] **步骤 2：本地运行 Compose 冒烟栈**

Run: `make compose-smoke && make e2e-smoke`
Expected: Playwright 库存 spec 通过；既有 spec 无回归。
- [ ] **步骤 3：提交**

```bash
git add frontend/e2e/inventory.spec.ts
git commit -m "test(e2e): 库存主链路 Playwright 端到端用例"
```

---

## 任务 42：最终验证

- [ ] **步骤 1：后端全量**

Run: `cd backend && ./mvnw -q test`
Expected: 全绿（含 4a + 4b 新增盘点测试）。

- [ ] **步骤 2：前端全量**

Run: `npm --prefix frontend test -- --run`
Expected: 全绿。

- [ ] **步骤 3：后端构建**

Run: `make backend-build`
Expected: BUILD SUCCESS。

- [ ] **步骤 4：前端构建**

Run: `make frontend-build`
Expected: vue-tsc 与 vite build 成功。

- [ ] **步骤 5：Compose 冒烟 + E2E 冒烟**

Run: `make compose-smoke && make e2e-smoke`
Expected: 全绿。

- [ ] **步骤 6：ModularityTests + diff**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test` && `git diff --check`
Expected: PASS、无空白错误。

- [ ] **步骤 7：记录完成**

在 `.workbuddy/memory/2026-07-25.md`（或当日文件）追加「阶段四 4b 完成：盘点后端 + 桌面主链路 + Playwright」，记录 HEAD、各命令通过情况。

- [ ] **步骤 8：提交记录**

```bash
git add .workbuddy/memory/2026-07-25.md
git commit -m "docs: 记录阶段四 4b 盘点与桌面主链路完成状态"
```

---

## 阶段 4b 验收门槛

- 盘点工作流（创建草稿/更新/补录账面为零/刷新/确认/取消）端点可用且由 Testcontainers 覆盖；范围任一库存变化使整单确认失败；差异条目 reason 必填；全新批次先入库再刷新盘点。
- 桌面 `/inventory` 四页签（当前库存/批次/流水/盘点）端到端可用；分步对话框覆盖入库（新建+补充）/领用/报损/移位/盘点/冲正；序列号重复警告；归档物品入库拒绝但历史可操作。
- `AppShell` 侧栏启用「库存管理」、顶栏「库存操作」下拉；物品与位置详情库存摘要与入口。
- Owner/Admin 可冲正与一致性检查；Member 隐藏按钮且直接 API 调用被 403；跨家庭隔离。
- 幂等重试复用同一 `Idempotency-Key`；改变业务内容后重置 key。
- 服务端数据不进长期 Pinia 缓存；沿用「松间账册」视觉体系。
- `make backend-test`、`make frontend-test`、`make backend-build`、`make frontend-build`、`make e2e-smoke`、`make compose-smoke`、`ModularityTests`、`git diff --check` 全绿。

## 阶段四完整性（4a + 4b 合并后）

达成 spec §10.3 最终门槛：所有数量可由不可变流水重建、并发不致负库存或重复扣减、移位/盘点/冲正原子性、完整桌面库存主链路可用。临期/低库存任务与通知（阶段五）与报表/CSV/搜索（阶段六）明确不在阶段四范围。
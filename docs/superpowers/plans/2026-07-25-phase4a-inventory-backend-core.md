# 阶段四 4a：库存后端核心 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `inventory` 后端模块的批次、库存位、不可变流水、入库/领用/报损/移位/冲正/一致性检查与幂等，产出可用的 `/api/v1/inventory/**` REST API 且全部由 Testcontainers 覆盖。

**Architecture:** 新增 Spring Modulith `inventory` 模块，单向依赖 `household`/`catalog`/`location`/`system` 的公开 `Api`。每个库存命令在单事务内完成「按 UUID 顺序锁定批次 → SELECT...FOR UPDATE 锁定/创建库存位 → 写不可变流水 + 更新库存位 + 幂等记录 + 审计 + 公开事件登记」原子提交。流水真且不可改，冲正只插补偿行。

**Tech Stack:** Java 25、Spring Boot 4.1.x、Spring Modulith 2.0.5、MyBatis-Plus 3.5.16、Flyway、PostgreSQL 17、JUnit 5、Mockito、AssertJ、Testcontainers、MockMvc。

**覆盖 spec：** `docs/superpowers/specs/2026-07-25-phase4-inventory-design.md`（不含 §6.6 盘点工作流、§9 桌面端——属于 4b）。

---

## 计划范围

仅后端 `inventory` 模块核心引擎：迁移、模块骨架、持久化、幂等、入库（新建/补充批次）、领用、报损、移位、冲正、一致性检查、REST 端点与测试。**不**实现盘点草稿/确认、前端、Playwright、Compose 改动——这些在 4b。

## 前置条件

- 工作树干净，HEAD 为 `f0db3f2`（阶段四 spec）。
- 已交付 V1–V10 迁移与 `system`/`identity`/`household`/`catalog`/`location`/`file` 模块。
- `CatalogApi.requireActiveItem/requireItem/requireUnit`、`LocationApi.requireLocation/markReferenced`、`HouseholdApi.requireActiveMember/hasAtLeastRole/MemberRole`、`SystemApi.recordAudit/AuditEvent` 已存在。
- 执行前用 `superpowers:using-git-worktrees` 建隔离工作树（或直接在 main 上按用户偏好执行）。

## 目标文件清单

**Create（后端）：**
- `backend/src/main/java/com/zija/inventory/package-info.java` — `@ApplicationModule`，依赖 `household`/`catalog`/`location`/`system`。
- `backend/src/main/java/com/zija/inventory/InventoryApi.java` — 公开只读端口 + `StockChangedEvent` record。
- `backend/src/main/java/com/zija/inventory/internal/InventoryController.java` — REST 端点。
- `backend/src/main/java/com/zija/inventory/internal/InventoryService.java` — 命令编排、权限、事务、审计、事件。
- `backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java` — Problem Details 映射。
- `backend/src/main/java/com/zija/inventory/internal/LotService.java` — 批次创建/修正。
- `backend/src/main/java/com/zija/inventory/internal/StockCommandService.java` — 入库/领用/报损/移位。
- `backend/src/main/java/com/zija/inventory/internal/ReversalService.java` — 冲正。
- `backend/src/main/java/com/zija/inventory/internal/ConsistencyCheckService.java` — 只读比对。
- `backend/src/main/java/com/zija/inventory/internal/IdempotencyService.java` — 幂等键比对。
- `backend/src/main/java/com/zija/inventory/internal/QuantityPrecision.java` — 数量精度校验助手（纯函数）。
- `backend/src/main/java/com/zija/inventory/internal/event/InventoryEventPublisher.java` — 事务内事件登记 + 提交后投递。
- `backend/src/main/java/com/zija/inventory/internal/persistence/{LotEntity,StockPositionEntity,MovementEntity,IdempotencyRecordEntity}.java`
- `backend/src/main/java/com/zija/inventory/internal/persistence/{LotMapper,StockPositionMapper,MovementMapper,IdempotencyRecordMapper,ConsistencyCheckMapper}.java`
- `backend/src/main/resources/mapper/inventory/{LotMapper,StockPositionMapper,MovementMapper,IdempotencyRecordMapper,ConsistencyCheckMapper}.xml`
- `backend/src/main/resources/db/migration/V11__create_inventory_core.sql`

**Create（测试）：**
- `backend/src/test/java/com/zija/inventory/internal/QuantityPrecisionTest.java`
- `backend/src/test/java/com/zija/inventory/internal/IdempotencyServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/LotServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/StockCommandServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/ReversalServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/ConsistencyCheckServiceIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/InventoryEndpointIntegrationTest.java`
- `backend/src/test/java/com/zija/inventory/internal/InventoryConcurrencyIntegrationTest.java`
- 复用 `backend/src/test/java/com/zija/ModularityTests.java`（任务 19 修改）。

**Modify：**
- `backend/src/main/java/com/zija/ZijaSecurityConfiguration.java` — 无需改动：`.anyRequest().authenticated()` 已覆盖 inventory 端点；冲正/一致性检查由服务层 `hasAtLeastRole(ADMIN)` 校验。

每个任务结束提交一次（中文 body + 英文前缀）。`Idempotency-Key` 由请求头 `X-Idempotency-Key` 传入。

---

## 任务 1：数据库迁移——库存核心表

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__create_inventory_core.sql`

- [ ] **步骤 1：创建 V11 迁移文件**

```sql
-- 批次
CREATE TABLE inventory_lot (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    item_id         UUID NOT NULL,
    purchase_date   DATE,
    production_date DATE,
    expiry_date     DATE,
    lot_number      VARCHAR(80),
    serial_number   VARCHAR(120),
    memo            VARCHAR(4000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_lot_household UNIQUE (household_id, id),
    CONSTRAINT fk_inventory_lot_item FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id)
);

CREATE INDEX idx_inventory_lot_household_item ON inventory_lot(household_id, item_id);
CREATE INDEX idx_inventory_lot_household_expiry ON inventory_lot(household_id, expiry_date);

-- 库存位（投影）
CREATE TABLE inventory_stock_position (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    lot_id       UUID NOT NULL,
    location_id  UUID NOT NULL,
    quantity     NUMERIC(20,6) NOT NULL,
    revision     BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_stock_position UNIQUE (household_id, lot_id, location_id),
    CONSTRAINT ck_inventory_stock_position_nonneg CHECK (quantity >= 0),
    CONSTRAINT fk_inventory_stock_position_lot FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_inventory_stock_position_location FOREIGN KEY (household_id, location_id)
        REFERENCES location_location(household_id, id)
);

CREATE INDEX idx_inventory_stock_position_household_location
    ON inventory_stock_position(household_id, location_id);
CREATE INDEX idx_inventory_stock_position_household_lot
    ON inventory_stock_position(household_id, lot_id);
```

> 注：`location_location` 表名以现有 V10 `location` 迁移实际表名为准。若 V10 表名不同，执行步骤 2 验证会立即暴露；按实际表名修正后提交。

```sql
-- 不可变库存流水
CREATE TABLE inventory_movement (
    id                 UUID PRIMARY KEY,
    household_id       UUID NOT NULL REFERENCES household(id),
    lot_id             UUID NOT NULL,
    item_id            UUID NOT NULL,
    type               VARCHAR(20) NOT NULL,
    quantity           NUMERIC(20,6) NOT NULL,
    from_location_id   UUID,
    to_location_id     UUID,
    reason             VARCHAR(120),
    memo               VARCHAR(4000),
    operator_account_id UUID NOT NULL,
    business_time      TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key    VARCHAR(100) NOT NULL,
    reversal_of        UUID,
    CONSTRAINT ck_inventory_movement_type
        CHECK (type IN ('INBOUND','CONSUME','LOSS','ADJUSTMENT','TRANSFER','REVERSAL')),
    CONSTRAINT ck_inventory_movement_quantity_pos CHECK (quantity > 0),
    CONSTRAINT fk_inventory_movement_lot FOREIGN KEY (household_id, lot_id)
        REFERENCES inventory_lot(household_id, id),
    CONSTRAINT fk_inventory_movement_reversal_of FOREIGN KEY (id)
        REFERENCES inventory_movement(id)
);

CREATE INDEX idx_inventory_movement_household_lot
    ON inventory_movement(household_id, lot_id, created_at);
CREATE INDEX idx_inventory_movement_household_item
    ON inventory_movement(household_id, item_id, created_at);
CREATE INDEX idx_inventory_movement_household_type
    ON inventory_movement(household_id, type, created_at);
CREATE INDEX idx_inventory_movement_household_location
    ON inventory_movement(household_id, coalesce(from_location_id, to_location_id));
CREATE INDEX idx_inventory_movement_idempotency
    ON inventory_movement(household_id, idempotency_key);
CREATE INDEX idx_inventory_movement_reversal_of
    ON inventory_movement(reversal_of);

-- 幂等结果登记
CREATE TABLE inventory_idempotency_record (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(120) NOT NULL,
    movement_id      UUID,
    response_payload JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_idempotency UNIQUE (household_id, idempotency_key)
);
```

- [ ] **步骤 2：确认 location 表名**

Run: `grep -rn "CREATE TABLE location" backend/src/main/resources/db/migration/V10__create_location.sql`
Expected: 输出实际表名（如 `location_location`）。若不同，回到步骤 1 修正 `fk_inventory_stock_position_location` 与索引引用，使表名一致。

- [ ] **步骤 3：本地空库验证迁移**

Run: `make dev-db`（若未启动）；`make backend-test -k CreateInventoryCoreMigration` 会在任务 11 的端点测试中顺带走 Flyway。当前先用任意现有 Testcontainers 测试启动应用：
Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS（迁移在 Testcontainers 空库上由 Flyway 执行成功；若 V11 有语法错误会在此时报错）。

- [ ] **步骤 4：提交**

```bash
git add backend/src/main/resources/db/migration/V11__create_inventory_core.sql
git commit -m "feat(inventory): 新增 V11 库存核心表迁移

批次、库存位、不可变流水与幂等记录表，含唯一约束、非负约束、
类型 CHECK 与外键；流水表无 UPDATE/DELETE 路径由服务层保证。"
```

---

## 任务 2：inventory 模块骨架与公开契约

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/package-info.java`
- Create: `backend/src/main/java/com/zija/inventory/InventoryApi.java`

- [ ] **步骤 1：创建 package-info**

```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {"household", "catalog", "location", "system"}
)
package com.zija.inventory;
```

- [ ] **步骤 2：创建 InventoryApi 公开接口**

```java
package com.zija.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 库存模块公共 API，提供库存位、批次流水只读查询与公开库存变更事件类型。
 * 仅暴露记录类型与查询端口，不含命令；命令由本模块 REST 端点接收。
 */
public interface InventoryApi {

    Optional<StockPositionInfo> findStockPosition(UUID householdId, UUID lotId, UUID locationId);

    List<StockPositionInfo> stockPositionsOfItem(UUID householdId, UUID itemId);

    List<MovementInfo> movementsOfLot(UUID householdId, UUID lotId);

    record StockPositionInfo(
            UUID lotId,
            UUID locationId,
            BigDecimal quantity,
            long revision,
            OffsetDateTime updatedAt
    ) {}

    record MovementInfo(
            UUID id,
            UUID lotId,
            UUID itemId,
            String type,
            BigDecimal quantity,
            UUID fromLocationId,
            UUID toLocationId,
            String reason,
            UUID operatorAccountId,
            OffsetDateTime businessTime,
            OffsetDateTime createdAt,
            UUID idempotencyKey,
            UUID reversalOf
    ) {}
}
```

- [ ] **步骤 3：创建 StockChangedEvent 公开事件类型**

放在公开包 `com.zija.inventory`（模块外部可订阅）：

```java
package com.zija.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存变更公开事件。每个成功库存命令发布一条，带全局唯一 eventId 供消费者去重。
 * 阶段四只建立发布契约，不实现阶段五提醒消费者。
 */
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
        UUID idempotencyKey
) {}
```

- [ ] **步骤 4：编译验证**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS（无类引用即可编译）。

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/zija/inventory/package-info.java \
        backend/src/main/java/com/zija/inventory/InventoryApi.java
# StockChangedEvent 一并
git add backend/src/main/java/com/zija/inventory/StockChangedEvent.java
git commit -m "feat(inventory): 新增模块骨架与公开契约 InventoryApi/StockChangedEvent"
```

---

## 任务 3：持久化实体类

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/LotEntity.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StockPositionEntity.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/MovementEntity.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/IdempotencyRecordEntity.java`

- [ ] **步骤 1：创建 LotEntity**

遵循 `ItemEntity` 约定：MyBatis-Plus 注解、4 空格缩进、字段 + getter/setter、`@Version`。

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_lot")
public class LotEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID itemId;
    private LocalDate purchaseDate;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String lotNumber;
    private String serialNumber;
    private String memo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;
    // 各字段 getter/setter，按 ItemEntity 风格逐字段补全
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getItemId() { return itemId; } public void setItemId(UUID v) { this.itemId = v; }
    public LocalDate getPurchaseDate() { return purchaseDate; } public void setPurchaseDate(LocalDate v) { this.purchaseDate = v; }
    public LocalDate getProductionDate() { return productionDate; } public void setProductionDate(LocalDate v) { this.productionDate = v; }
    public LocalDate getExpiryDate() { return expiryDate; } public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public String getLotNumber() { return lotNumber; } public void setLotNumber(String v) { this.lotNumber = v; }
    public String getSerialNumber() { return serialNumber; } public void setSerialNumber(String v) { this.serialNumber = v; }
    public String getMemo() { return memo; } public void setMemo(String v) { this.memo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { this.version = v; }
}
```

- [ ] **步骤 2：创建 StockPositionEntity**

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_stock_position")
public class StockPositionEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID lotId;
    private UUID locationId;
    private BigDecimal quantity;
    private Long revision;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getLotId() { return lotId; } public void setLotId(UUID v) { this.lotId = v; }
    public UUID getLocationId() { return locationId; } public void setLocationId(UUID v) { this.locationId = v; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal v) { this.quantity = v; }
    public Long getRevision() { return revision; } public void setRevision(Long v) { this.revision = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
```

- [ ] **步骤 3：创建 MovementEntity**

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_movement")
public class MovementEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID lotId;
    private UUID itemId;
    private String type;
    private BigDecimal quantity;
    private UUID fromLocationId;
    private UUID toLocationId;
    private String reason;
    private String memo;
    private UUID operatorAccountId;
    private OffsetDateTime businessTime;
    private OffsetDateTime createdAt;
    private String idempotencyKey;
    private UUID reversalOf;
    // 逐字段 getter/setter
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getLotId() { return lotId; } public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; } public void setItemId(UUID v) { this.itemId = v; }
    public String getType() { return type; } public void setType(String v) { this.type = v; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal v) { this.quantity = v; }
    public UUID getFromLocationId() { return fromLocationId; } public void setFromLocationId(UUID v) { this.fromLocationId = v; }
    public UUID getToLocationId() { return toLocationId; } public void setToLocationId(UUID v) { this.toLocationId = v; }
    public String getReason() { return reason; } public void setReason(String v) { this.reason = v; }
    public String getMemo() { return memo; } public void setMemo(String v) { this.memo = v; }
    public UUID getOperatorAccountId() { return operatorAccountId; } public void setOperatorAccountId(UUID v) { this.operatorAccountId = v; }
    public OffsetDateTime getBusinessTime() { return businessTime; } public void setBusinessTime(OffsetDateTime v) { this.businessTime = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public UUID getReversalOf() { return reversalOf; } public void setReversalOf(UUID v) { this.reversalOf = v; }
}
```

- [ ] **步骤 4：创建 IdempotencyRecordEntity**

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName("inventory_idempotency_record")
public class IdempotencyRecordEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String idempotencyKey;
    private String requestHash;
    private UUID movementId;
    private Map<String, Object> responsePayload;
    private OffsetDateTime createdAt;
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getRequestHash() { return requestHash; } public void setRequestHash(String v) { this.requestHash = v; }
    public UUID getMovementId() { return movementId; } public void setMovementId(UUID v) { this.movementId = v; }
    public Map<String, Object> getResponsePayload() { return responsePayload; } public void setResponsePayload(Map<String, Object> v) { this.responsePayload = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
```

- [ ] **步骤 5：编译并提交**

Run: `cd backend && ./mvnw -q compile` → 期望 SUCCESS。
```bash
git add backend/src/main/java/com/zija/inventory/internal/persistence/
git commit -m "feat(inventory): 新增库存持久化实体类 Lot/StockPosition/Movement/IdempotencyRecord"
```

---

## 任务 4：Mapper 接口与 XML（行锁与条件更新）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/LotMapper.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/StockPositionMapper.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/MovementMapper.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/IdempotencyRecordMapper.java`
- Create: `backend/src/main/java/com/zija/inventory/internal/persistence/ConsistencyCheckMapper.java`
- Create: `backend/src/main/resources/mapper/inventory/LotMapper.xml`
- Create: `backend/src/main/resources/mapper/inventory/StockPositionMapper.xml`
- Create: `backend/src/main/resources/mapper/inventory/MovementMapper.xml`
- Create: `backend/src/main/resources/mapper/inventory/IdempotencyRecordMapper.xml`
- Create: `backend/src/main/resources/mapper/inventory/ConsistencyCheckMapper.xml`

- [ ] **步骤 1：LotMapper 接口 + XML**

接口（继承 `BaseMapper<LotEntity>`）：

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LotMapper extends BaseMapper<LotEntity> {

    /** 按给定批次 id 集合按 UUID 顺序加锁。 */
    void lockByIds(@Param("householdId") UUID householdId, @Param("ids") List<UUID> ids);

    /** 检测同一物品下序列号是否已存在（用于重复警告，不阻止）。 */
    int countByItemAndSerial(@Param("householdId") UUID householdId,
                             @Param("itemId") UUID itemId,
                             @Param("serialNumber") String serialNumber);
}
```

`LotMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.LotMapper">

    <select id="lockByIds">
        SELECT id FROM inventory_lot
        WHERE household_id = #{householdId}
          AND id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY id
        FOR UPDATE
    </select>

    <select id="countByItemAndSerial" resultType="int">
        SELECT COUNT(*) FROM inventory_lot
        WHERE household_id = #{householdId}
          AND item_id = #{itemId}
          AND serial_number = #{serialNumber}
    </select>
</mapper>
```

- [ ] **步骤 2：StockPositionMapper 接口 + XML（核心行锁）**

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StockPositionMapper extends BaseMapper<StockPositionEntity> {

    /** 锁定单个库存位行（不存在返回 null）。事务内调用。 */
    StockPositionEntity lockOne(@Param("householdId") UUID householdId,
                                @Param("lotId") UUID lotId,
                                @Param("locationId") UUID locationId);

    /** 条件加增：quantity = quantity + ?, revision = revision + 1，WHERE 依 household/lot/location。 */
    int addQuantity(@Param("householdId") UUID householdId,
                    @Param("lotId") UUID lotId,
                    @Param("locationId") UUID locationId,
                    @Param("delta") BigDecimal delta);

    /** 条件扣减：仅当 quantity - delta >= 0 时更新，并 revision+1；返回受影响行数（0 表示不足）。 */
    int subtractIfSufficient(@Param("householdId") UUID householdId,
                             @Param("lotId") UUID lotId,
                             @Param("locationId") UUID locationId,
                             @Param("delta") BigDecimal delta);

    IPage<StockPositionEntity> findPage(Page<StockPositionEntity> page,
                                         @Param("householdId") UUID householdId,
                                         @Param("itemId") UUID itemId,
                                         @Param("locationId") UUID locationId,
                                         @Param("orderBy") String orderBy);
}
```

`StockPositionMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.StockPositionMapper">

    <select id="lockOne" resultType="com.zija.inventory.internal.persistence.StockPositionEntity">
        SELECT id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at
        FROM inventory_stock_position
        WHERE household_id = #{householdId} AND lot_id = #{lotId} AND location_id = #{locationId}
        FOR UPDATE
    </select>

    <update id="addQuantity">
        UPDATE inventory_stock_position
        SET quantity = quantity + #{delta},
            revision = revision + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE household_id = #{householdId} AND lot_id = #{lotId} AND location_id = #{locationId}
    </update>

    <update id="subtractIfSufficient">
        UPDATE inventory_stock_position
        SET quantity = quantity - #{delta},
            revision = revision + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE household_id = #{householdId}
          AND lot_id = #{lotId}
          AND location_id = #{locationId}
          AND quantity - #{delta} >= 0
    </update>

    <select id="findPage" resultType="com.zija.inventory.internal.persistence.StockPositionEntity">
        SELECT sp.id, sp.household_id, sp.lot_id, sp.location_id, sp.quantity, sp.revision,
               sp.created_at, sp.updated_at
        FROM inventory_stock_position sp
        <where>
            sp.household_id = #{householdId}
            <if test="itemId != null">AND EXISTS (SELECT 1 FROM inventory_lot l WHERE l.id = sp.lot_id AND l.item_id = #{itemId})</if>
            <if test="locationId != null">AND sp.location_id = #{locationId}</if>
        </where>
        ORDER BY ${orderBy}
    </select>
</mapper>
```

- [ ] **步骤 3：MovementMapper 接口 + XML**

```java
package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MovementMapper extends BaseMapper<MovementEntity> {

    /** 查询某流水的反向流水是否存在（冲正检查）。 */
    int countReversalOf(@Param("householdId") UUID householdId, @Param("originalId") UUID originalId);

    /** 按 (household, lot) 聚合签名（健壮重建用）。 */
    List<MovementEntity> findByLot(@Param("householdId") UUID householdId, @Param("lotId") UUID lotId);

    IPage<MovementEntity> findPage(Page<MovementEntity> page,
                                   @Param("householdId") UUID householdId,
                                   @Param("type") String type,
                                   @Param("itemId") UUID itemId,
                                   @Param("locationId") UUID locationId,
                                   @Param("operatorAccountId") UUID operatorAccountId,
                                   @Param("from") java.time.OffsetDateTime from,
                                   @Param("to") java.time.OffsetDateTime to,
                                   @Param("orderBy") String orderBy);
}
```

`MovementMapper.xml`：实现 `countReversalOf`、`findByLot`、`findPage`（`findPage` 按 `type`/`itemId`/`location in (from,to)`/`operator`/`business_time BETWEEN` 过滤，`ORDER BY ${orderBy}`）。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.MovementMapper">

    <select id="countReversalOf" resultType="int">
        SELECT COUNT(*) FROM inventory_movement
        WHERE household_id = #{householdId} AND reversal_of = #{originalId}
    </select>

    <select id="findByLot" resultType="com.zija.inventory.internal.persistence.MovementEntity">
        SELECT id, household_id, lot_id, item_id, type, quantity, from_location_id, to_location_id,
               reason, memo, operator_account_id, business_time, created_at, idempotency_key, reversal_of
        FROM inventory_movement
        WHERE household_id = #{householdId} AND lot_id = #{lotId}
        ORDER BY created_at
    </select>

    <select id="findPage" resultType="com.zija.inventory.internal.persistence.MovementEntity">
        SELECT id, household_id, lot_id, item_id, type, quantity, from_location_id, to_location_id,
               reason, memo, operator_account_id, business_time, created_at, idempotency_key, reversal_of
        FROM inventory_movement
        <where>
            household_id = #{householdId}
            <if test="type != null and type != ''">AND type = #{type}</if>
            <if test="itemId != null">AND item_id = #{itemId}</if>
            <if test="locationId != null">AND (from_location_id = #{locationId} OR to_location_id = #{locationId})</if>
            <if test="operatorAccountId != null">AND operator_account_id = #{operatorAccountId}</if>
            <if test="from != null">AND business_time &gt;= #{from}</if>
            <if test="to != null">AND business_time &lt; #{to}</if>
        </where>
        ORDER BY ${orderBy}
    </select>
</mapper>
```

- [ ] **步骤 4：IdempotencyRecordMapper 接口 + XML**

```java
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {
    /** 锁定幂等记录行（不存在返回 null）。事务内争用唯一约束。 */
    IdempotencyRecordEntity lockByKey(@Param("householdId") UUID householdId,
                                      @Param("key") String key);
}
```

XML：`SELECT ... FROM inventory_idempotency_record WHERE household_id=#{householdId} AND idempotency_key=#{key} FOR UPDATE`，返回类型 `com.zija.inventory.internal.persistence.IdempotencyRecordEntity`，含 `response_payload`（JSONB，MyBatis-Plus 默认按对象 Map 处理；若类型处理器缺失，先以 `String` 接收再在服务层解析——见任务 11 注）。

- [ ] **步骤 5：ConsistencyCheckMapper（只读聚合）**

```java
@Mapper
public interface ConsistencyCheckMapper {
    /** 当前库存位集合（household 或 item 过滤）。 */
    List<StockPositionEntity> currentPositions(@Param("householdId") UUID householdId,
                                               @Param("itemId") UUID itemId);
    /** 按库存位签名汇总流水应有数量。 */
    List<StockPositionEntity> expectedFromMovements(@Param("householdId") UUID householdId,
                                                    @Param("itemId") UUID itemId);
}
```

`expectedFromMovements` XML：按 `(lot_id, location_id)` 分别累加 `INBOUND/TRANSFER(to)/ADJUSTMENT(正向)/REVERSAL(反向效应)` 与 `CONSUME/LOSS/TRANSFER(from)/ADJUSTMENT(负向)/REVERSAL`，产出每库存位应有 quantity。聚合 SQL 由实现者按 `type` 用 `CASE WHEN` 写出加项与减项；该 SQL 是一致性检查核心，必须经 Testcontainers 验证（任务 15）。

- [ ] **步骤 6：编译并提交**

Run: `cd backend && ./mvnw -q compile` → 期望 SUCCESS。
```bash
git add backend/src/main/java/com/zija/inventory/internal/persistence/ \
        backend/src/main/resources/mapper/inventory/
git commit -m "feat(inventory): 新增 Mapper 与 XML 行锁/条件更新/聚合查询"
```

---

## 任务 5：错误码异常与 InventoryExceptionHandler

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/InventoryInsufficientStockException.java`
- Create: 其余八类异常（见下）
- Create: `backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java`

所有异常为 `RuntimeException` 子类、无参纯类型（由 handler 统一映射 errorCode）。

- [ ] **步骤 1：创建异常类**

逐个创建以下空体异常类（package `com.zija.inventory.internal`，均 `extends RuntimeException`，每个仅默认构造与 `(String msg)` 构造并 `super`）：

| 类名 | errorCode | HTTP |
|---|---|---|
| `InventoryInsufficientStockException` | `INVENTORY_INSUFFICIENT_STOCK` | 409 |
| `InventoryQuantityPrecisionInvalidException` | `INVENTORY_QUANTITY_PRECISION_INVALID` | 422 |
| `InventoryIdempotencyConflictException` | `INVENTORY_IDEMPOTENCY_CONFLICT` | 409 |
| `InventoryArchivedItemException` | `INVENTORY_ARCHIVED_ITEM` | 409 |
| `InventoryLotVersionConflictException` | `INVENTORY_LOT_VERSION_CONFLICT` | 409 |
| `InventoryMovementAlreadyReversedException` | `INVENTORY_MOVEMENT_ALREADY_REVERSED` | 409 |
| `InventoryReversalNotAllowedException` | `INVENTORY_REVERSAL_NOT_ALLOWED` | 409 |
| `InventoryReversalWouldNegativeException` | `INVENTORY_REVERSAL_WOULD_NEGATIVE` | 409 |

示例：
```java
package com.zija.inventory.internal;

public class InventoryInsufficientStockException extends RuntimeException {
    public InventoryInsufficientStockException() { super(); }
    public InventoryInsufficientStockException(String msg) { super(msg); }
}
```

- [ ] **步骤 2：创建 InventoryExceptionHandler**

复用 `CatalogExceptionHandler` 的 `problem(...)` 私有方法与 `requestId` 属性 `zija.request-id`，`assignableTypes = InventoryController.class`。逐异常 `@ExceptionHandler` 映射到上表 HTTP/errorCode，标题用中文一句话。`INVENTORY_REVERSAL_NOT_ALLOWED` 与 `ALREADY_REVERSED`、`WOULD_NEGATIVE` 均 409。

- [ ] **步骤 3：编译并提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/*Exception.java \
        backend/src/main/java/com/zija/inventory/internal/InventoryExceptionHandler.java
git commit -m "feat(inventory): 新增库存稳定错误码与 Problem Details 异常处理"
```

---

## 任务 6：QuantityPrecision 数量校验助手（TDD）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/QuantityPrecision.java`
- Test: `backend/src/test/java/com/zija/inventory/internal/QuantityPrecisionTest.java`

- [ ] **步骤 1：写失败测试**

```java
package com.zija.inventory.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityPrecisionTest {

    @Test
    void integerUnitRejectsFraction() {
        assertThatThrownBy(() -> QuantityPrecision.require(0, new BigDecimal("1.5")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }

    @Test
    void integerUnitAcceptsIntegers() {
        assertThat(QuantityPrecision.require(0, new BigDecimal("3"))).isEqualByComparingTo("3");
        assertThat(QuantityPrecision.require(0, new BigDecimal("3.00"))).isEqualByComparingTo("3");
    }

    @Test
    void threeScaleRejectsFourth() {
        assertThatThrownBy(() -> QuantityPrecision.require(3, new BigDecimal("0.1234")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }

    @Test
    void threeScaleAcceptsUpToThree() {
        assertThat(QuantityPrecision.require(3, new BigDecimal("0.123"))).isEqualByComparingTo("0.123");
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> QuantityPrecision.require(2, new BigDecimal("-1")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }
}
```

- [ ] **步骤 2：验证失败**

Run: `cd backend && ./mvnw -q -Dtest=QuantityPrecisionTest test`
Expected: 编译失败（`QuantityPrecision` 不存在）。

- [ ] **步骤 3：实现 QuantityPrecision**

```java
package com.zija.inventory.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class QuantityPrecision {
    private QuantityPrecision() {}

    static BigDecimal require(int decimalScale, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InventoryQuantityPrecisionInvalidException();
        }
        try {
            var scaled = quantity.setScale(decimalScale, RoundingMode.UNNECESSARY);
            if (scaled.signum() <= 0) throw new InventoryQuantityPrecisionInvalidException();
            return scaled;
        } catch (ArithmeticException ex) {
            throw new InventoryQuantityPrecisionInvalidException();
        }
    }
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=QuantityPrecisionTest test` → PASS。
```bash
git add backend/src/main/java/com/zija/inventory/internal/QuantityPrecision.java \
        backend/src/test/java/com/zija/inventory/internal/QuantityPrecisionTest.java
git commit -m "feat(inventory): 数量精度校验助手与单元测试"
```

---

## 任务 7：IdempotencyService（TDD，Testcontainers）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/IdempotencyService.java`
- Test: `backend/src/test/java/com/zija/inventory/internal/IdempotencyServiceIntegrationTest.java`

幂等服务方法签名：
```java
/** 在调用方事务内执行。返回命中记录则调用方跳过命令并回放 responsePayload；返回 Optional.empty() 则继续执行命令并在成功后由调用方记录。 */
Optional<IdempotencyRecordEntity> lockOrFind(UUID householdId, String idempotencyKey, String requestHash);
/** 命令成功后登记结果。 */
void recordSuccess(UUID householdId, String key, String requestHash, UUID movementId, java.util.Map<String,Object> responsePayload);
```

`requestHash`：对命令类型 + 关键业务字段做稳定拼接后 `SHA-256` hex（实现中提供 `RequestHashing.sha256(String)` 工具，或复用 `MessageDigest`）。说明在类注释。

- [ ] **步骤 1：写失败测试（Testcontainers）**

测试类复用 `ItemEndpointIntegrationTest` 的 DB 装配、`TRUNCATE` 与 seed 套路，但 truncate 列表追加 `inventory_idempotency_record`。测试用例：

1. `lockOrFind` 首次返回 empty；`recordSuccess` 后再 `lockOrFind` 同键同 hash 返回命中记录。
2. 同键不同 hash 再 `lockOrFind` 仍返回首次记录（由 `lockById` 锁定后服务层比对 hash 抛 `InventoryIdempotencyConflictException`）。
3. 事务回滚不留记录：在事务内 `lockOrFind` 返回 empty 后抛异常，外层断言表中无该键记录。

示例：
```java
@Test
void firstCallReturnsEmptyThenRecordSuccessMakesSecondHit(@Autowired IdempotencyRecordMapper mapper) {
    var hh = seedHousehold();
    var svc = idempotencyService;
    var tx = txManager;
    newTx(() -> {
        assertThat(svc.lockOrFind(hh, "k1", "HASH_A")).isEmpty();
        svc.recordSuccess(hh, "k1", "HASH_A", UUID.randomUUID(), java.util.Map.of("ok", true));
    });
    newTx(() -> {
        var hit = svc.lockOrFind(hh, "k1", "HASH_A");
        assertThat(hit).isPresent();
        assertThat(hit.get().getRequestHash()).isEqualTo("HASH_A");
    });
}

@Test
void sameKeyDifferentHashThrowsConflict(@Autowired IdempotencyRecordMapper mapper) {
    var hh = seedHousehold();
    newTx(() -> svc.recordSuccess(hh, "k2", "HASH_A", UUID.randomUUID(), java.util.Map.of()));
    assertThatThrownBy(() -> newTx(() -> svc.lockOrFind(hh, "k2", "HASH_B")))
            .isInstanceOf(InventoryIdempotencyConflictException.class);
}

@Test
void rollbackLeavesNoRecord() {
    var hh = seedHousehold();
    assertThatThrownBy(() -> newTx(() -> {
        svc.lockOrFind(hh, "k3", "HASH_C");
        throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);
    newTx(() -> assertThat(mapper.selectList(null).stream().noneMatch(r -> "k3".equals(r.getIdempotencyKey()))).isTrue());
}
```

> 说明：`lockOrFind` 必须 `SELECT ... FOR UPDATE`（事务内），读到记录后比对 `requestHash`：不同则直接抛 `InventoryIdempotencyConflictException`；同则返回 `Optional.of(record)`；无记录返回 `empty()`。测试用 `PlatformTransactionManager` 手动开 `TransactionTemplate`。

- [ ] **步骤 2：验证失败**

Run: `cd backend && ./mvnw -q -Dtest=IdempotencyServiceIntegrationTest test`
Expected: 编译失败（类缺失）。

- [ ] **步骤 3：实现 IdempotencyService**

```java
@Service
public class IdempotencyService {
    private final IdempotencyRecordMapper mapper;
    public IdempotencyService(IdempotencyRecordMapper m) { this.mapper = m; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecordEntity> lockOrFind(UUID householdId, String key, String requestHash) {
        var existing = mapper.lockByKey(householdId, key);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new InventoryIdempotencyConflictException();
            }
            return Optional.of(existing);
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(UUID householdId, String key, String requestHash,
                              UUID movementId, Map<String,Object> payload) {
        var e = new IdempotencyRecordEntity();
        e.setId(UUID.randomUUID());
        e.setHouseholdId(householdId);
        e.setIdempotencyKey(key);
        e.setRequestHash(requestHash);
        e.setMovementId(movementId);
        e.setResponsePayload(payload);
        e.setCreatedAt(OffsetDateTime.now());
        try { mapper.insert(e); }
        catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发争用：另一线程先写入，按其记录比对 hash 判断
            var r = mapper.lockByKey(householdId, key);
            if (r != null && !requestHash.equals(r.getRequestHash()))
                throw new InventoryIdempotencyConflictException();
        }
    }
}
```

- [ ] **步骤 4：实现 RequestHashing 工具**

```java
package com.zija.inventory.internal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
final class RequestHashing {
    private RequestHashing() {}
    static String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var hex = new StringBuilder();
            for (byte b : md.digest(s.getBytes(StandardCharsets.UTF_8)))
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```

- [ ] **步骤 5：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=IdempotencyServiceIntegrationTest test` → PASS。
```bash
git add backend/src/main/java/com/zija/inventory/internal/IdempotencyService.java \
        backend/src/main/java/com/zija/inventory/internal/RequestHashing.java \
        backend/src/test/java/com/zija/inventory/internal/IdempotencyServiceIntegrationTest.java
git commit -m "feat(inventory): 幂等服务与同键冲突/回滚不留记录测试"
```

---

## 任务 8：LotService 批次创建与修正（TDD）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/LotService.java`
- Test: `backend/src/test/java/com/zija/inventory/internal/LotServiceIntegrationTest.java`

公开方法（package 内）：
```java
UUID createLot(UUID householdId, UUID itemId, LocalDate purchaseDate, LocalDate productionDate,
               LocalDate expiryDate, String lotNumber, String serialNumber, String memo);
LotEntity requireLot(UUID householdId, UUID lotId);
/** 修正批次资料：item_id 不可改；返回重复序列号警告标志。 */
LotEntity updateLotMeta(UUID householdId, UUID lotId, int clientVersion,
                        LocalDate purchaseDate, LocalDate productionDate, LocalDate expiryDate,
                        String lotNumber, String serialNumber, String memo);
/** 新建批次时序列号重复检测（只警告）。 */
boolean serialNumberDuplicated(UUID householdId, UUID itemId, String serialNumber);
```

测试要点（Testcontainers）：
1. `createLot` 校验物品存在（`catalogApi.requireActiveItem`）；归档物品 → `InventoryArchivedItemException`。
2. `updateLotMeta` 用旧 `version` → `InventoryLotVersionConflictException`；成功后 `version` +1。
3. `updateLotMeta` 拒绝改 `item_id`（服务层忽略或抛错——服务层只对允许字段 SET，`item_id` 永不进 update 列）。
4. `serialNumberDuplicated` 同物品同序列号返回 true；不同物品返回 false。

实现：`createLot` 用 `catalogApi.requireActiveItem`；`updateLotMeta` 用 MyBatis-Plus `updateById`（带 `@Version`）+ MyBatis-Plus 乐观锁拦截器（已全局注册）。`item_id` 通过仅 `select` 校验比对、不写入实现不可改。

- [ ] **步骤 1：写失败测试** — 覆盖上述四点（复用 seed 套路，`@MockitoBean CatalogApi`：`when(catalogApi.requireActiveItem(hh, itemId)).thenReturn(itemInfo)`；归档场景 `thenThrow(new CatalogArchivedItemException-like)` 或抛 `InventoryArchivedItemException`——这里统一由 LotService 自己校验 `itemInfo.status()` 为 `ARCHIVED` 时抛 `InventoryArchivedItemException`）。

- [ ] **步骤 2：验证失败** — `cd backend && ./mvnw -q -Dtest=LotServiceIntegrationTest test`，编译失败。

- [ ] **步骤 3：实现 LotService** — 注入 `LotMapper`、`CatalogApi`；`createLot` 调 `requireActiveItem`（归档则抛 `InventoryArchivedItemException`）、insert；`updateLotMeta` 用 Lambda update 仅设允许字段，行数 0 抛版本冲突。

- [ ] **步骤 4：验证通过 + 提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/LotService.java \
        backend/src/test/java/com/zija/inventory/internal/LotServiceIntegrationTest.java
git commit -m "feat(inventory): 批次创建与资料修正（item_id 不可改、版本冲突、序列号重复警告）"
```

---

## 任务 9：StockCommandService 入库（新建批次）（TDD）

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/StockCommandService.java`
- Modify: 任务 10–13 持续在该类追加方法
- Test: `backend/src/test/java/com/zija/inventory/internal/StockCommandServiceIntegrationTest.java`

入库新建批次统一方法：
```java
InboundResult inboundNewLot(UUID householdId, UUID accountId, UUID locationId, InboundNewLotCommand cmd);
record InboundNewLotCommand(UUID itemId, BigDecimal quantity, LocalDate purchaseDate, LocalDate productionDate,
                            LocalDate expiryDate, String lotNumber, String serialNumber, String memo,
                            String idempotencyKey) {}
record InboundResult(UUID lotId, UUID locationId, UUID movementId, BigDecimal quantityAfter, boolean serialDuplicated) {}
```

事务编排（`@Transactional`）：按 UUID 排序锁批次集合（此处只有自身新建批次，无既有批次需锁；可跳过 lot 锁）→ `QuantityPrecision.require(unit.decimalScale, quantity)` → `createLot` → `LocationApi.requireLocation` + `LocationApi.markReferenced` → `StockPositionMapper.lockOne`（可能 null）→ 若 null 则 insert 新库存位（quantity=0,revision=0）→ `addQuantity` → insert `INBOUND` 流水（`to_location_id=location`，`quantity`，`idempotency_key`）→ `IdempotencyService.recordSuccess` → `SystemApi.recordAudit(action="INVENTORY_INBOUND")` → `InventoryEventPublisher.publish(StockChangedEvent)` → 返回 `InboundResult`。

`requestHash = RequestHashing.sha256("INBOUND_NEW:"+itemId+":"+locationId+":"+quantity.scale()+":"+quantity.stripTrailingZeros()+":"+lotNumber+":"+serialNumber+":"+expiryDate)`。

测试要点：
1. 入库后库存位 `quantity` 正确、`revision=1`；`INBOUND` 流水一条，`to_location_id` 填、`from_location_id` null。
2. 归档物品入库 → `InventoryArchivedItemException`，无库存位、无流水。
3. `LocationApi.markReferenced` 被调用（用 `@MockitoBean LocationApi` + `verify`）。
4. 重复入库到新批次（同物品两次）得到两个不同批次两个库存位。

- [ ] **步骤 1–4**：TDD 循环（写测试 → 失败 → 实现 → 通过 → 提交）。实现需创建 `InventoryEventPublisher`（任务 16 才完整；此处先建最小桩：同步 `ApplicationEventPublisher.publishEvent(StockChangedEvent)`，事务提交后由 Spring 默认同步发布；阶段四只需类型存在即可，可靠投递在 4b 不做——保持简单。事件唯一 `eventId` 用 `UUID.randomUUID()`）。

```java
package com.zija.inventory.internal.event;
import com.zija.inventory.StockChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class InventoryEventPublisher {
    private final ApplicationEventPublisher publisher;
    public InventoryEventPublisher(ApplicationEventPublisher p) { this.publisher = p; }
    public void publish(StockChangedEvent e) { publisher.publishEvent(e); }
}
```

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/StockCommandService.java \
        backend/src/main/java/com/zija/inventory/internal/event/InventoryEventPublisher.java \
        backend/src/test/java/com/zija/inventory/internal/StockCommandServiceIntegrationTest.java
git commit -m "feat(inventory): 新建批次入库流水与库存位、归档物品拒绝、位置引用标记"
```

---

## 任务 10：补充现有批次入库

**Files:** Modify `StockCommandService.java`（追加 `inboundExistingLot(householdId, accountId, locationId, lotId, quantity, memo, idempotencyKey)`）；追加测试。

事务：按 UUID 锁批次（`LotMapper.lockByIds([lotId])`）→ 校验物品 `ACTIVE`（`requireActiveItem`；归档抛 `InventoryArchivedItemException`）→ 精度校验 → `requireLocation`+`markReferenced` → 锁/建库存位 → `addQuantity` → insert `INBOUND` 流水（`lot_id` 为现有）→ 幂等 + 审计 + 事件。

测试：补充后库存位 quantity 累加、revision+1；归档物品补充入库被拒。

- [ ] TDD 循环 + 提交 `feat(inventory): 补充现有批次入库`。

---

## 任务 11：领用 consume

**Files:** Modify `StockCommandService`（追加 `consume(householdId, accountId, lotId, locationId, quantity, reason, memo, idempotencyKey)`）；追加测试。

事务：锁批次 → `catalogApi.requireItem`（**允许归档物品领用**，不要求 ACTIVE）→ 精度 → `requireLocation` → `lockOne`（null 抛 `InventoryInsufficientStockException`）→ `subtractIfSufficient`（返回 0 抛 `InventoryInsufficientStockException`）→ insert `CONSUME` 流水（`from_location_id`）→ 幂等 + 审计 + 事件。

测试：领用扣减、revision+1；扣减至 0 允许（quantity>=0）；超过余量抛 `INVENTORY_INSUFFICIENT_STOCK` 且无流水、库存位不变；归档物品可领用。

- [ ] TDD 循环 + 提交 `feat(inventory): 领用流水与余量不足拒绝`。

---

## 任务 12：报损 loss

**Files:** Modify `StockCommandService`（追加 `loss(...)`，`reason` 必填校验）；追加测试。

与 consume 同结构，`reason` 为空或空白抛 `InventoryQuantityPrecisionInvalidException` 复用不合适——这里用 Bean Validation 在控制器层校验 `reason` 非空（`@NotBlank`），服务层二次防御：`reason == null || reason.isBlank()` 抛 `InventoryReversalNotAllowedException`? 否——应抛校验相关。新增轻量做法：服务层抛 `IllegalArgumentException` 由全局处理器兜底为 400；或复用 `jakarta.validation`。**决定**：控制器层 `@NotBlank` + `@Valid` DTO，服务层不做额外异常（保持纯粹）。流水类型 `LOSS`，端点 `reason` 字段 `@NotBlank`。

测试：报损扣减；`reason` 空白被 `@Valid` 拒（控制器测试在任务 17）。

- [ ] TDD + 提交 `feat(inventory): 报损流水与原因必填校验`。

---

## 任务 13：移位 transfer

**Files:** Modify `StockCommandService`（追加 `transfer(householdId, accountId, lotId, fromLocationId, toLocationId, quantity, memo, idempotencyKey)`）；追加测试。

校验 `fromLocationId.equals(toLocationId)` → 抛 `InventoryIdempotencyConflictException`? 不语义。**决定**：抛 `IllegalArgumentException`（控制器层 `@AssertFalse`/`@NotNull` + 服务层 `Objects.equals` 抛 `InventoryReversalNotAllowedException`? 不匹配）。最稳妥：新增语义清晰做法——服务层相等时抛 `InventoryIdempotencyConflictException` 错误。审查后改用专门：客户端会先行校验，服务层抛 `IllegalArgumentException("源与目标相同")` 由全局兜底 400 + VALIDATION_FAILED。**最终**：控制器 DTO 用 `@AssertTrue(expr = from != to)` 给出 400 VALIDATION_FAILED；服务层防御性 `Objects.equals` 抛 `IllegalStateException`（不应到达）。

事务锁顺序：按 UUID 排序对 `from` 与 `to` 两个库存位 `lockOne`（注意：先锁源、再锁目标；为统一顺序，先按 `lotId` 锁批次，再按 `fromLocationId.compareTo(toLocationId)` 顺序逐一 `lockOne`）→ 精度 → `subtractIfSufficient(from)`（0 抛不足）→ 锁/建目标库存位 → `addQuantity(to)` → insert **一条** `TRANSFER` 流水（`from_location_id`、`to_location_id`、`quantity`）→ 幂等 + 审计 + 事件。

原子性由单事务保证：`addQuantity` 失败不会发生（数值合法），但若目标库存位新建后任何异常，整事务回滚——不存在「只扣不加」。

测试：
1. 移位后源减、目标增、各 revision+1；仅一条 `TRANSFER` 流水。
2. 源不足抛 `INVENTORY_INSUFFICIENT_STOCK`，源与目标均不变（整回滚）。
3. 目标库存位不存在则同事务创建。
4. 源=目标 被控制器 400 拒（任务 17 验证；本任务服务层防御）。
5. 归档物品可移位（`requireItem`）。

- [ ] TDD + 提交 `feat(inventory): 移位单事务两端原子更新与不足回滚`。

---

## 任务 14：冲正 reversal（TDD）

**Files:** Create `ReversalService.java`；Test `ReversalServiceIntegrationTest.java`。

方法：
```java
ReversalResult reverse(UUID householdId, UUID accountId, UUID originalMovementId, String reason, String memo, String idempotencyKey);
record ReversalResult(UUID reversalMovementId, UUID lotId) {}
```

流程（`@Transactional`，=Owner/Admin 由 `InventoryService` 调用前用 `hasAtLeastRole(ADMIN)` 校验，ReversalService 自身再防御性校验——但角色上下文不在此层；权限校验集中在 `InventoryService`，本任务独立测冲正逻辑，权限测试在任务 17）：
1. 载入原流水（`movementMapper.selectById`）；不存在 → 用 `InventoryReversalNotAllowedException`（409，语义「目标流水不存在」）。
2. `movementMapper.countReversalOf(hh, originalId) > 0` → `InventoryMovementAlreadyReversedException`。
3. 类型允许：全部六类可冲正；若为 `REVERSAL` 自身 → `InventoryReversalNotAllowedException`。
4. 计算反向影响数量与端点：
   - `INBOUND`（补到 to_location）：反向扣减 `to_location_id`，`REVERSAL` `quantity = +原quantity`，`from_location_id = to`、语义反向。
   - `CONSUME`/`LOSS`（从 from 扣）：反向加回 `from_location_id`。
   - `TRANSFER`：反向从 `to_location_id` 扣、`from_location_id` 加。
   - `ADJUSTMENT`：按其影响的库存位反向。
   实现统一：冲正对每个受影响库存位做「等量反向」的 `subtractIfSufficient`/`addQuantity`；若任一 `subtractIfSufficient` 返回 0 → `InventoryReversalWouldNegativeException`，整事务回滚（原始流水与已建库存位状态不变）。
5. insert `REVERSAL` 流水（`quantity = +原quantity`，`reversal_of = originalId`，端点同原流水端点表达反向）。
6. 幂等 + 审计（`action=INVENTORY_REVERSAL`，detail.reversalOf）+ 事件。
7. 原始流水永不被 UPDATE 或 DELETE。

测试：
1. `CONSUME` 冲正：库存位加回、新增一条 `REVERSAL` 流水、原流水仍在；`countReversalOf` 变 1。
2. 已冲正再冲正 → `INVENTORY_MOVEMENT_ALREADY_REVERSED`。
3. 冲正会导致负（先领用至余量低于冲正量）→ `INVENTORY_REVERSAL_WOULD_NEGATIVE`，状态不变。
4. `REVERSAL` 流水自身不可冲正 → `INVENTORY_REVERSAL_NOT_ALLOWED`。
5. 原流水不存在 → `INVENTORY_REVERSAL_NOT_ALLOWED`。

- [ ] TDD 循环 + 提交 `feat(inventory): 冲正补偿流水与已冲正/会负拒绝、原始流水保留`。

---

## 任务 15：一致性检查 ConsistencyCheckService（TDD）

**Files:** Create `ConsistencyCheckMapper` 已在任务 4；Create `ConsistencyCheckService.java`；Test `ConsistencyCheckServiceIntegrationTest.java`。

方法：
```java
List<Discrepancy> check(UUID householdId, UUID itemIdFilter);
record Discrepancy(UUID lotId, UUID locationId, BigDecimal expected, BigDecimal actual) {}
```

流程（只读，`@Transactional(readOnly=true)`）：`currentPositions(...)` 与 `expectedFromMovements(...)` 两个列表按 `(lotId, locationId)` 对齐比对，`actual != expected` 入差异清单。**绝不**修改库存位或流水。

`expectedFromMovements` SQL 用 `CASE WHEN type IN ('INBOUND','TRANSFER'且to=该位,'ADJUSTMENT'正向,'REVERSAL'反向为加) THEN +quantity WHEN ... THEN -quantity END` 累加——由实现者写出精确聚合（必须在任务 4 的 XML 已就绪）。`ADJUSTMENT` 与 `REVERSAL` 的正负由端点（`from`/`to`）与符号决定；为简化，`ADJUSTMENT` 流水存「对目标的带符号影响量」并只填 `to_location_id`，负调整存负 quantity。**修正**：V11 CHECK `quantity > 0` 会拒绝 `ADJUSTMENT`/`REVERSAL` 负值。阶段四调整：`ADJUSTMENT` 与 `REVERSAL` 不存负 `quantity`，而用「端点 + 正 quantity + 语义」表达方向（`ADJUSTMENT` 减少时 `from_location_id` 填、`to_location_id` null；增加时反之）。据此写聚合：每库存位的期望 = `SUM(CASE WHEN sign=+ THEN +quantity ELSE -quantity END)`，其中 sign 由 type 与该库存位是 to 端还是 from 端决定。

> 决策：阶段四 `ADJUSTMENT`/`REVERSAL` 用端点表达方向，`quantity` 恒正，与 V11 CHECK 兼容。在任务 4 XML 描述中已隐含；本任务的聚合 SQL 是核心，必须经 Testcontainers 验证。

测试：
1. 正常入库/领用后一致性检查无差异。
2. 人为改 `inventory_stock_position.quantity`（SQL 直接 update 绕过服务）后检查发现差异，且无任何库被写（断言 quantity 未被该端点改变）。
3. 过滤 `itemId` 只检查该物品库存位。

- [ ] TDD + 提交 `feat(inventory): 一致性检查只读比对流水与库存位`。

---

## 任务 16：InventoryService 编排与审计

**Files:** Create `InventoryService.java`；注入 `HouseholdApi`/`SystemApi`/`LotService`/`StockCommandService`/`ReversalService`/`ConsistencyCheckService`/`IdempotencyService`。

职责：
- 命令入口：先 `householdApi.requireActiveMember(accountId)` → `hasAtLeastRole` 仅对冲正/一致性检查强制 `ADMIN`，不足抛 `org.springframework.security.access.AccessDeniedException`（由全局 AccessDeniedHandler 返 403）。
- 调用对应 service（service 已带 `@Transactional`，编排层不再嵌套事务；权限校验在事务外）。
- `recordAudit` 在各 service 内已记录；编排层不重复。
- 实现 `InventoryApi` 的只读端口（`findStockPosition`/`stockPositionsOfItem`/`movementsOfLot`）。

测试：在端点测试（任务 17）与并发测试（任务 18）间接覆盖；本任务可加一个单测验证 `InventoryApi` 实现委托到 mapper。

- [ ] 编译 + 提交 `feat(inventory): 编排层与权限校验、InventoryApi 只读端口实现`。

---

## 任务 17：InventoryController REST 端点与 MockMvc 测试

**Files:** Create `InventoryController.java`；Test `InventoryEndpointIntegrationTest.java`。

端点（`/api/v1/inventory/**`，`@RequireMember`，写入带 `csrf()`）：

只读：
- `GET /inventory/stock-positions`
- `GET /inventory/lots`
- `GET /inventory/lots/{lotId}`
- `GET /inventory/movements`
- `GET /inventory/consistency-report`（Owner/Admin）

写入（请求体 DTO，`@Valid`，`Idempotency-Key` 由 `@RequestHeader` 传入）：
- `POST /inventory/lots`（新建批次入库）
- `POST /inventory/inbound`
- `POST /inventory/consume`
- `POST /inventory/loss`（`reason` `@NotBlank`）
- `POST /inventory/transfer`（DTO `@AssertTrue(expr = from != to)`）
- `POST /inventory/movements/{id}/reverse`（Owner/Admin）
- `PUT /inventory/lots/{id}`（修正批次资料，`version` 必填）

测试（MockMvc + `@MockitoBean CatalogApi/LocationApi/HouseholdApi/SystemApi` 按需；注意 `HouseholdApi.requireActiveMember` 与 `hasAtLeastRole` 的 stub）：
1. 入库 → 200 + 返回 lotId/movementId + 流水可查。
2. 领用不足 → 409 `INVENTORY_INSUFFICIENT_STOCK`。
3. 报损 `reason` 空白 → 400 `VALIDATION_FAILED`。
4. 移位 源=目标 → 400。
5. 冲正：Owner 200；Member 调用 `POST /movements/{id}/reverse` → 403（`hasAtLeastRole(ADMIN)` false 由 `HouseholdApi` stub 返回 false；`AccessDeniedHandler` 返 403）。
6. 一致性检查：Owner 200；Member → 403。
7. 跨家庭隔离：seed 两个家庭，用 A 家庭成员请求 B 家庭的 lotId（路径不含 householdId，由 principal 推断 household）→ 因 lot 属 B 家庭而 service 校验 household 必须匹配 principal 的 household → 返回 404/409；具体由 `LotService.requireLot(householdId, lotId)` 不匹配抛 `InventoryReversalNotAllowedException`/或新 `NotFoundException`→映射。**决定**：不匹配统一抛 `InventoryReversalNotAllowedException`(409) 不优雅；新增 `InventoryNotFoundException`→404 映射。本任务补该异常+handler 映射。
8. `Idempotency-Key` 重复同请求 → 200 且首次结果，流水仅一条（jsonPath total/再次查询 movements 计数）。
9. OpenAPI 包含 `/inventory` 端点：启动测试后断言 springdoc 生成含路径（用 `mockMvc.perform(get("/v3/api-docs"))` 一次性在任务 19 不再单测，本任务加一个轻量断言）。

- [ ] TDD 循环 + 提交 `feat(inventory): REST 端点、权限 403、跨家庭隔离、幂等重放与错误码映射`。

---

## 任务 18：并发 Testcontainers 测试

**Files:** Create `InventoryConcurrencyIntegrationTest.java`。

用 `CountDownLatch` + `ExecutorService` 并发线程，全部在同一 Testcontainers PostgreSQL 上。

用例：
1. **两消费者超支同一库存位**：先入库 quantity=2，两线程各领用 2；断言：恰好一个成功 200、另一 409 `INVENTORY_INSUFFICIENT_STOCK`，最终 quantity=0、`CONSUME` 流水恰好 1 条。用 `Callable` + `invokeAll` 收集结果计数。
2. **移位单边不可提交**：源 quantity=1，目标不存在；移位 quantity=1 成功后源=0、目标=1；并发两移位各 1 → 一成功一 409，源/目标状态守恒。
3. **相同幂等只产一条流水**：同 `Idempotency-Key` 并发两次入库 → 流水表 `idempotency_key` 计数 1。
4. **流水不可变 / 重建**：直接 SQL `UPDATE inventory_movement SET quantity=99 WHERE id=?` 被数据库拒绝? 不——DB 不阻止 UPDATE。改为：一致性检查后人为改库存位 → 发现差异（已在任务 15）。本测试额外断言：所有数量可由流水重建——对一批次做「入库 5 + 领用 2 + 报损 1」后，`ConsistencyCheckService.check` 无差异，且每个库存位 quantity 等于 `expectedFromMovements`。

- [ ] TDD + 提交 `test(inventory): 并发超支/单边/幂等唯一流水/可重建 Testcontainers 测试`。

---

## 任务 19：ModularityTests 扩展

**Files:** Modify `backend/src/test/java/com/zija/ModularityTests.java`。

- [ ] **步骤 1：新增 inventory 模块断言**

```java
@Test
void inventoryModuleExistsAndDependenciesAreValid() {
    assertThat(modules.getModuleByName("inventory")).isPresent();
    modules.verify();
}
```

- [ ] **步骤 2：验证**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS（`ApplicationModules.verify()` 通过；若 inventory 误引用 `catalog.internal` 等会失败）。

- [ ] **步骤 3：提交**

```bash
git add backend/src/test/java/com/zija/ModularityTests.java
git commit -m "test(inventory): 模块边界验证新增 inventory 断言"
```

---

## 任务 20：最终验证

- [ ] **步骤 1：后端全量测试**

Run: `cd backend && ./mvnw -q test`
Expected: 全绿，含新增 inventory 测试与既有 catalog/location/identity/household/system 测试无回归。

- [ ] **步骤 2：后端构建**

Run: `make backend-build`
Expected: `BUILD SUCCESS`。

- [ ] **步骤 3：layout/diff 检查**

Run: `git diff --check`
Expected: 无空白错误。

- [ ] **步骤 4：记录完成**

在 `.workbuddy/memory/2026-07-25.md`（若不存在则创建）追加一条「阶段四 4a 完成」记录：最终 HEAD、`make backend-test` 通过、`make backend-build` 通过、`ModularityTests` 通过。

- [ ] **步骤 5：提交记录**

```bash
git add .workbuddy/memory/2026-07-25.md
git commit -m "docs: 记录阶段四 4a 后端库存核心完成状态"
```

---

## 阶段 4a 验收门槛

- `/api/v1/inventory/**` 入库（新建/补充）、领用、报损、移位、冲正、一致性检查端点可用并由 MockMvc + Testcontainers 覆盖。
- 并发不造成负库存或重复扣减；移位单边不可提交；相同幂等只产一条流水。
- Owner/Admin 可冲正与一致性检查；Member 直接 API 调用被 403；跨家庭隔离拒绝 B 家庭操作 A。
- `ModularityTests` 通过；`inventory` 不反向被 catalog/location 依赖。
- `make backend-test`、`make backend-build`、`git diff --check` 全绿。

## 与 4b 的衔接

4a 完成且验收后，编写 `4b` plan：盘点草稿/确认（§6.6）、桌面端四页签与分步对话框与 Item/Location 详情库存入口（§9）、Vitest + Playwright、Compose 无需改动。4b 依赖 4a 的 REST 端点，不再触碰后端核心引擎。
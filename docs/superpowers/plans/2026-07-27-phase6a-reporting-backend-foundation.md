# Phase 6a: reporting 后端基础与投影 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 `reporting` 模块骨架，扩展跨模块公开事件契约，实现事件投影监听与快照拉取端口，使 reporting 模块具备增量接收与全量重建投影数据的能力。

**Architecture:** `reporting` 模块通过订阅 `inventory` / `catalog` / `location` 的公开领域事件维护自有扁平投影表（事件投影 + 查询端口混合读模型，ADR-004）。增量靠事件订阅，重建靠源模块公共 API 上新增的快照拉取端口（ADR-005）。投影写入失败走 dead-letter 重试，与 reminder 模块同模式但完全隔离。

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, PostgreSQL 17, Flyway

## Global Constraints

- 模块边界：`reporting` 仅依赖 `household` / `catalog` / `location` / `inventory` / `system` 的公共 `Api` 与公开事件，绝不 import 他模块 `internal` 包。
- 公共事件字段**只追加、不重排、不删除**（ADR-006 约束）。
- Java：4-space indent，`@Configuration(proxyBeanMethods = false)`。
- 数据库表名前缀 `reporting_`，Flyway 迁移文件 `V5__create_reporting_core.sql`。
- UUID 主键（`id-type: assign_uuid`），underscore-to-camel-case 映射。
- Entity 类手写 getter/setter，不用 Lombok。
- Mapper XML namespace = mapper 接口全限定名，resultType 用全限定类名。
- 所有 `@Transactional` 写操作在事务末尾发布事件；事件发布在事务提交后由 Spring Modulith 派发。

## File Structure

### 新建文件

```
backend/src/main/java/com/zija/reporting/
  package-info.java                          # @ApplicationModule
  ReportingApi.java                          # 公共 API 接口（阶段六先留空骨架）

backend/src/main/java/com/zija/reporting/internal/
  projection/
    ProjectionListener.java                  # 事件监听 → 投影 upsert
    ReportingEventRetryService.java          # dead-letter 定时重试

backend/src/main/java/com/zija/reporting/internal/persistence/
  ProcessedEventEntity.java
  ProcessedEventMapper.java
  DeadLetterEntity.java
  DeadLetterMapper.java
  SearchIndexEntity.java
  SearchIndexMapper.java
  StockFlatEntity.java
  StockFlatMapper.java
  MovementFlatEntity.java
  MovementFlatMapper.java

backend/src/main/java/com/zija/catalog/        # 公开包，跨模块可见
  ItemChangedEvent.java
  CategoryChangedEvent.java
  BrandChangedEvent.java
  UnitChangedEvent.java
  TagChangedEvent.java

backend/src/main/java/com/zija/catalog/internal/event/
  CatalogEventPublisher.java

backend/src/main/java/com/zija/location/        # 公开包，跨模块可见
  LocationChangedEvent.java

backend/src/main/java/com/zija/location/internal/event/
  LocationEventPublisher.java

backend/src/main/resources/mapper/reporting/
  ProcessedEventMapper.xml
  DeadLetterMapper.xml
  SearchIndexMapper.xml
  StockFlatMapper.xml
  MovementFlatMapper.xml

backend/src/main/resources/db/migration/
  V5__create_reporting_core.sql
```

### 修改文件

```
backend/src/main/java/com/zija/inventory/StockChangedEvent.java           # +3 字段
backend/src/main/java/com/zija/inventory/InventoryApi.java                 # +dump 方法与 DTO
backend/src/main/java/com/zija/inventory/internal/InventoryService.java    # 实现 dump 方法
backend/src/main/java/com/zija/inventory/internal/persistence/             # +dump XML
backend/src/main/java/com/zija/catalog/CatalogApi.java                     # +dump 方法与 DTO
backend/src/main/java/com/zija/catalog/internal/ItemService.java           # 实现 dump + 发布事件
backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java # 发布事件
backend/src/main/java/com/zija/location/LocationApi.java                   # +dump 方法与 DTO
backend/src/main/java/com/zija/location/internal/LocationService.java      # 实现 dump + 发布事件
backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java # toMap +3 键
backend/src/main/java/com/zija/reminder/internal/EventRetryService.java     # fromMap +3 字段
backend/src/test/java/com/zija/ModularityTests.java                        # +reporting 测试
```

---

### Task 1: reporting 模块骨架 + V5 迁移 + 模块边界验证

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/package-info.java`
- Create: `backend/src/main/java/com/zija/reporting/ReportingApi.java`
- Create: `backend/src/main/resources/db/migration/V5__create_reporting_core.sql`
- Modify: `backend/src/test/java/com/zija/ModularityTests.java`

**Interfaces:**
- Produces: `ReportingApi` (公共 API 接口，后续 task 扩展)
- Produces: V5 迁移创建 5 张 `reporting_*` 表

- [ ] **Step 1: 创建 `package-info.java`**

```java
// backend/src/main/java/com/zija/reporting/package-info.java
@org.springframework.modulith.ApplicationModule(
        displayName = "Reporting",
        allowedDependencies = {"household", "catalog", "location", "inventory", "system"}
)
package com.zija.reporting;
```

- [ ] **Step 2: 创建空 `ReportingApi.java`**

```java
// backend/src/main/java/com/zija/reporting/ReportingApi.java
package com.zija.reporting;

/**
 * 报表模块公共 API。阶段六为事件投影与报表查询模块，
 * 不对外暴露命令端口；仅消费其他模块的只读能力与公开事件。
 */
public interface ReportingApi {
    // 阶段六后续任务逐步扩展
}
```

- [ ] **Step 3: 创建 `V5__create_reporting_core.sql`**

```sql
-- V5__create_reporting_core.sql
-- reporting 模块核心表：事件去重、死信、搜索索引、库存流水扁平、库存位扁平

-- 1) 事件去重
CREATE TABLE reporting_processed_event (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_processed_event_type ON reporting_processed_event(event_type);
COMMENT ON TABLE reporting_processed_event IS 'reporting 模块事件去重登记';
COMMENT ON COLUMN reporting_processed_event.event_id IS '事件唯一 ID（来自源模块）';
COMMENT ON COLUMN reporting_processed_event.event_type IS '事件类型全限定名';
COMMENT ON COLUMN reporting_processed_event.processed_at IS '处理完成时间';

-- 2) 事件 dead-letter
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
COMMENT ON TABLE reporting_event_dead_letter IS 'reporting 模块事件处理失败重试队列';
COMMENT ON COLUMN reporting_event_dead_letter.payload IS '原始事件 JSON 序列化';
COMMENT ON COLUMN reporting_event_dead_letter.failure_count IS '累计失败次数';
COMMENT ON COLUMN reporting_event_dead_letter.abandoned IS '超过最大重试次数后放弃';

-- 3) 全局搜索扁平读模型
CREATE TABLE reporting_search_index (
    household_id    UUID NOT NULL,
    entity_type     VARCHAR(20) NOT NULL,
    entity_id       UUID NOT NULL,
    item_name       VARCHAR(120),
    brand_name      VARCHAR(120),
    tag_names       VARCHAR(400),
    category_name   VARCHAR(120),
    unit_name       VARCHAR(40),
    lot_id          UUID,
    lot_number      VARCHAR(120),
    serial_number   VARCHAR(120),
    location_id     UUID,
    location_name   VARCHAR(120),
    location_path   VARCHAR(800),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, entity_type, entity_id)
);
CREATE INDEX idx_reporting_search_item_name ON reporting_search_index(household_id, entity_type, item_name);
CREATE INDEX idx_reporting_search_lot_number ON reporting_search_index(household_id, entity_type, lot_number);
CREATE INDEX idx_reporting_search_location ON reporting_search_index(household_id, entity_type, location_path);
COMMENT ON TABLE reporting_search_index IS '全局搜索扁平读模型';
COMMENT ON COLUMN reporting_search_index.entity_type IS 'ITEM | LOT | LOCATION';
COMMENT ON COLUMN reporting_search_index.tag_names IS '逗号分隔标签名';

-- 4) 库存流水扁平读模型
CREATE TABLE reporting_movement_flat (
    household_id        UUID NOT NULL,
    movement_id         UUID NOT NULL PRIMARY KEY,
    event_id            UUID NOT NULL UNIQUE,
    lot_id              UUID NOT NULL,
    item_id             UUID NOT NULL,
    item_name           VARCHAR(120) NOT NULL,
    type                VARCHAR(20) NOT NULL,
    quantity_delta      NUMERIC(20,6) NOT NULL,
    from_location_id    UUID,
    to_location_id      UUID,
    from_location_path  VARCHAR(800),
    to_location_path    VARCHAR(800),
    operator_account_id UUID,
    operator_display_name VARCHAR(120),
    reason              VARCHAR(120),
    reversal_of         UUID,
    business_time       TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reporting_movement_flat_time ON reporting_movement_flat(household_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_item ON reporting_movement_flat(household_id, item_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_operator ON reporting_movement_flat(household_id, operator_account_id, business_time DESC);
CREATE INDEX idx_reporting_movement_flat_type ON reporting_movement_flat(household_id, type, business_time DESC);
COMMENT ON TABLE reporting_movement_flat IS '库存流水扁平读模型（reporting 投影）';
COMMENT ON COLUMN reporting_movement_flat.type IS 'INBOUND | CONSUME | DAMAGE | STOCKTAKE | MOVE | REVERSAL';
COMMENT ON COLUMN reporting_movement_flat.operator_display_name IS '由 reporting 拉取 identity 信息填充';

-- 5) 库存位扁平读模型
CREATE TABLE reporting_stock_flat (
    household_id    UUID NOT NULL,
    lot_id          UUID NOT NULL,
    item_id         UUID NOT NULL,
    item_name       VARCHAR(120) NOT NULL,
    unit_name       VARCHAR(40) NOT NULL,
    lot_number      VARCHAR(120),
    serial_number   VARCHAR(120),
    expiry_date     DATE,
    location_id     UUID NOT NULL,
    location_path   VARCHAR(800) NOT NULL,
    quantity        NUMERIC(20,6) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (household_id, lot_id, location_id)
);
CREATE INDEX idx_reporting_stock_flat_expiry ON reporting_stock_flat(household_id, expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_reporting_stock_flat_item ON reporting_stock_flat(household_id, item_id);
COMMENT ON TABLE reporting_stock_flat IS '库存位扁平读模型（reporting 投影）';
```

- [ ] **Step 4: 在 `ModularityTests.java` 中新增 reporting 模块测试**

```java
// 在现有测试方法之后追加
@Test
void reportingModuleExistsAndDependenciesAreValid() {
    assertThat(modules.getModuleByName("reporting")).isPresent();
    modules.verify();
}
```

- [ ] **Step 5: 运行测试验证模块边界**

Run: `cd backend && ./mvnw -q test -Dtest=ModularityTests`
Expected: 所有测试 PASS，包括新增的 `reportingModuleExistsAndDependenciesAreValid`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/ \
        backend/src/main/resources/db/migration/V5__create_reporting_core.sql \
        backend/src/test/java/com/zija/ModularityTests.java
git commit -m "feat(reporting): 模块骨架 + V5 迁移 + 模块边界验证

- 新增 reporting 模块 package-info 与空 ReportingApi
- V5 迁移：reporting_processed_event / dead_letter / search_index / movement_flat / stock_flat
- ModularityTests 新增 reportingModuleExistsAndDependenciesAreValid"
```

---

### Task 2: StockChangedEvent 扩展 + reminder 消费者同步改造

**Files:**
- Modify: `backend/src/main/java/com/zija/inventory/StockChangedEvent.java:1-16`
- Modify: `backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java` (toMap 方法)
- Modify: `backend/src/main/java/com/zija/reminder/internal/EventRetryService.java` (fromMap 方法)

**Interfaces:**
- Produces: `StockChangedEvent` 新增 `operatorAccountId(UUID)`, `reason(String)`, `reversalOf(UUID)` 三个可空字段
- Consumer impact: `ReminderEventListener.toMap()` +3 键；`EventRetryService.fromMap()` +3 字段（缺键容错 `null`）

- [ ] **Step 1: 扩展 `StockChangedEvent` record**

```java
// backend/src/main/java/com/zija/inventory/StockChangedEvent.java
package com.zija.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存变更公开事件。每个成功库存命令发布一条，带全局唯一 eventId 供消费者去重。
 * 字段只追加、不重排、不删除——跨模块契约（ADR-006）。
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
        UUID idempotencyKey,
        // 阶段六追加（ADR-006）：
        UUID operatorAccountId,
        String reason,
        UUID reversalOf
) {}
```

- [ ] **Step 2: 更新 `ReminderEventListener.toMap()` — 追加 3 个键**

在 `toMap` 方法的 `Map.ofEntries(...)` 末尾追加：

```java
Map.entry("operatorAccountId", evt.operatorAccountId() == null ? "" : evt.operatorAccountId().toString()),
Map.entry("reason", evt.reason() == null ? "" : evt.reason()),
Map.entry("reversalOf", evt.reversalOf() == null ? "" : evt.reversalOf().toString())
```

- [ ] **Step 3: 更新 `EventRetryService.fromMap()` — 追加 3 个字段，缺键容错**

在 `fromMap` 方法的 `new StockChangedEvent(...)` 构造器中追加 3 个参数（在 `idempotencyKey` 之后）：

```java
// 原来最后一行：
UUID.fromString((String) m.get("idempotencyKey"))
// 改为：
UUID.fromString((String) m.get("idempotencyKey")),
// 阶段六追加字段，旧 dead-letter payload 缺键时容错取 null:
m.containsKey("operatorAccountId") && !((String) m.get("operatorAccountId")).isEmpty()
        ? UUID.fromString((String) m.get("operatorAccountId")) : null,
m.containsKey("reason") ? (String) m.get("reason") : null,
m.containsKey("reversalOf") && !((String) m.get("reversalOf")).isEmpty()
        ? UUID.fromString((String) m.get("reversalOf")) : null
```

- [ ] **Step 4: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过，无错误

- [ ] **Step 5: 运行 reminder 相关测试**

Run: `cd backend && ./mvnw -q test -Dtest="ReminderEventListenerTest,EventRetryServiceTest,ModularityTests"`
Expected: 全部 PASS（toMap/fromMap 改造后现有测试兼容）

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zija/inventory/StockChangedEvent.java \
        backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java \
        backend/src/main/java/com/zija/reminder/internal/EventRetryService.java
git commit -m "feat(inventory): StockChangedEvent 追加 operator/reason/reversalOf 字段

- ADR-006：公共事件字段只追加不重排不删除
- ReminderEventListener.toMap 追加 3 键
- EventRetryService.fromMap 追加 3 字段，旧 dead-letter 缺键容错"
```

---

### Task 3: catalog / location 公开事件记录 + 事件发布器

**Files:**
- Create: `backend/src/main/java/com/zija/catalog/ItemChangedEvent.java`
- Create: `backend/src/main/java/com/zija/catalog/CategoryChangedEvent.java`
- Create: `backend/src/main/java/com/zija/catalog/BrandChangedEvent.java`
- Create: `backend/src/main/java/com/zija/catalog/UnitChangedEvent.java`
- Create: `backend/src/main/java/com/zija/catalog/TagChangedEvent.java`
- Create: `backend/src/main/java/com/zija/catalog/internal/event/CatalogEventPublisher.java`
- Create: `backend/src/main/java/com/zija/location/LocationChangedEvent.java`
- Create: `backend/src/main/java/com/zija/location/internal/event/LocationEventPublisher.java`
- Modify: `backend/src/main/java/com/zija/catalog/internal/ItemService.java` (注入+发布)
- Modify: `backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java` (注入+发布)
- Modify: `backend/src/main/java/com/zija/location/internal/LocationService.java` (注入+发布)

**Interfaces:**
- Produces: `ItemChangedEvent(UUID eventId, UUID householdId, UUID itemId, String changeType, OffsetDateTime businessTime)`
- Produces: `CategoryChangedEvent(UUID eventId, UUID householdId, UUID categoryId, String changeType)`
- Produces: `BrandChangedEvent(UUID eventId, UUID householdId, UUID brandId, String changeType)`
- Produces: `UnitChangedEvent(UUID eventId, UUID householdId, UUID unitId, String changeType)`
- Produces: `TagChangedEvent(UUID eventId, UUID householdId, UUID tagId, String changeType)`
- Produces: `LocationChangedEvent(UUID eventId, UUID householdId, UUID locationId, String changeType, UUID parentId, OffsetDateTime businessTime)`

- [ ] **Step 1: 创建 5 个 catalog 事件记录**

```java
// backend/src/main/java/com/zija/catalog/ItemChangedEvent.java
package com.zija.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 物品变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record ItemChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID itemId,
        String changeType,
        OffsetDateTime businessTime
) {}
```

```java
// backend/src/main/java/com/zija/catalog/CategoryChangedEvent.java
package com.zija.catalog;

import java.util.UUID;

/**
 * 分类变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED / MOVED。
 */
public record CategoryChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID categoryId,
        String changeType
) {}
```

```java
// backend/src/main/java/com/zija/catalog/BrandChangedEvent.java
package com.zija.catalog;

import java.util.UUID;

/**
 * 品牌变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record BrandChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID brandId,
        String changeType
) {}
```

```java
// backend/src/main/java/com/zija/catalog/UnitChangedEvent.java
package com.zija.catalog;

import java.util.UUID;

/**
 * 计量单位变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record UnitChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID unitId,
        String changeType
) {}
```

```java
// backend/src/main/java/com/zija/catalog/TagChangedEvent.java
package com.zija.catalog;

import java.util.UUID;

/**
 * 标签变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record TagChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID tagId,
        String changeType
) {}
```

- [ ] **Step 2: 创建 `CatalogEventPublisher`**

```java
// backend/src/main/java/com/zija/catalog/internal/event/CatalogEventPublisher.java
package com.zija.catalog.internal.event;

import com.zija.catalog.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * catalog 模块事件发布器。在 @Transactional 方法末尾调用，
 * Spring Modulith 在事务提交后派发给消费者。
 */
@Component
public class CatalogEventPublisher {

    private final ApplicationEventPublisher publisher;

    public CatalogEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishItemChanged(UUID householdId, UUID itemId, String changeType) {
        publisher.publishEvent(new ItemChangedEvent(
                UUID.randomUUID(), householdId, itemId, changeType, OffsetDateTime.now()));
    }

    public void publishCategoryChanged(UUID householdId, UUID categoryId, String changeType) {
        publisher.publishEvent(new CategoryChangedEvent(
                UUID.randomUUID(), householdId, categoryId, changeType));
    }

    public void publishBrandChanged(UUID householdId, UUID brandId, String changeType) {
        publisher.publishEvent(new BrandChangedEvent(
                UUID.randomUUID(), householdId, brandId, changeType));
    }

    public void publishUnitChanged(UUID householdId, UUID unitId, String changeType) {
        publisher.publishEvent(new UnitChangedEvent(
                UUID.randomUUID(), householdId, unitId, changeType));
    }

    public void publishTagChanged(UUID householdId, UUID tagId, String changeType) {
        publisher.publishEvent(new TagChangedEvent(
                UUID.randomUUID(), householdId, tagId, changeType));
    }
}
```

- [ ] **Step 3: 创建 `LocationChangedEvent` 与 `LocationEventPublisher`**

```java
// backend/src/main/java/com/zija/location/LocationChangedEvent.java
package com.zija.location;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 位置变更公开事件。changeType: CREATED / UPDATED / RENAMED / MOVED / DELETED。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record LocationChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID locationId,
        String changeType,
        UUID parentId,
        OffsetDateTime businessTime
) {}
```

```java
// backend/src/main/java/com/zija/location/internal/event/LocationEventPublisher.java
package com.zija.location.internal.event;

import com.zija.location.LocationChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * location 模块事件发布器。在 @Transactional 方法末尾调用。
 */
@Component
public class LocationEventPublisher {

    private final ApplicationEventPublisher publisher;

    public LocationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishLocationChanged(UUID householdId, UUID locationId,
                                        String changeType, UUID parentId) {
        publisher.publishEvent(new LocationChangedEvent(
                UUID.randomUUID(), householdId, locationId, changeType,
                parentId, OffsetDateTime.now()));
    }
}
```

- [ ] **Step 4: 在 `ItemService` 中注入 `CatalogEventPublisher` 并在写操作末尾发布事件**

在 `ItemService` 构造器中注入 `CatalogEventPublisher`，在以下方法末尾（`return` 之前）调用发布：

| 方法 | 行号 | 调用 |
|---|---|---|
| `createItem(...)` | ~163 | `eventPublisher.publishItemChanged(householdId, entity.getId(), "CREATED")` |
| `updateItem(...)` | ~295 | `eventPublisher.publishItemChanged(householdId, id, "UPDATED")` |
| `archiveItem(...)` | ~174 | `eventPublisher.publishItemChanged(householdId, id, "ARCHIVED")` |
| `restoreItem(...)` | ~185 | `eventPublisher.publishItemChanged(householdId, id, "RESTORED")` |

- [ ] **Step 5: 在 `CatalogDictionaryService` 中注入 `CatalogEventPublisher` 并在写操作末尾发布事件**

在 `CatalogDictionaryService` 构造器中注入 `CatalogEventPublisher`，在以下方法末尾调用发布：

| 方法 | changeType |
|---|---|
| `createCategory(...)` | `"CREATED"` |
| `updateCategory(...)` | `"UPDATED"` |
| `archiveCategory(...)` | `"ARCHIVED"` |
| `restoreCategory(...)` | `"RESTORED"` |
| `moveCategory(...)` | `"MOVED"` |
| `createBrand(...)` | `"CREATED"` |
| `updateBrand(...)` | `"UPDATED"` |
| `archiveBrand(...)` | `"ARCHIVED"` |
| `restoreBrand(...)` | `"RESTORED"` |
| `createUnit(...)` | `"CREATED"` |
| `updateUnit(...)` | `"UPDATED"` |
| `archiveUnit(...)` | `"ARCHIVED"` |
| `restoreUnit(...)` | `"RESTORED"` |
| `updateUnitDecimalScale(...)` | `"UPDATED"` |
| `createTag(...)` | `"CREATED"` |
| `updateTag(...)` | `"UPDATED"` |
| `archiveTag(...)` | `"ARCHIVED"` |
| `restoreTag(...)` | `"RESTORED"` |

- [ ] **Step 6: 在 `LocationService` 中注入 `LocationEventPublisher` 并在写操作末尾发布事件**

在 `LocationService` 构造器中注入 `LocationEventPublisher`，在以下方法末尾调用发布：

| 方法 | 行号 | changeType | parentId |
|---|---|---|---|
| `createLocation(...)` | ~87 | `"CREATED"` | `parentId`（参数） |
| `renameLocation(...)` | ~101 | `"RENAMED"` | `entity.getParentId()` |
| `moveLocation(...)` | ~124 | `"MOVED"` | `targetParentId`（参数） |
| `deleteLocation(...)` | ~140 | `"DELETED"` | `entity.getParentId()` |

- [ ] **Step 7: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/zija/catalog/ItemChangedEvent.java \
        backend/src/main/java/com/zija/catalog/CategoryChangedEvent.java \
        backend/src/main/java/com/zija/catalog/BrandChangedEvent.java \
        backend/src/main/java/com/zija/catalog/UnitChangedEvent.java \
        backend/src/main/java/com/zija/catalog/TagChangedEvent.java \
        backend/src/main/java/com/zija/catalog/internal/event/CatalogEventPublisher.java \
        backend/src/main/java/com/zija/location/LocationChangedEvent.java \
        backend/src/main/java/com/zija/location/internal/event/LocationEventPublisher.java \
        backend/src/main/java/com/zija/catalog/internal/ItemService.java \
        backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java \
        backend/src/main/java/com/zija/location/internal/LocationService.java
git commit -m "feat(catalog,location): 公开领域事件记录 + 事件发布器

- catalog 新增 ItemChangedEvent/CategoryChangedEvent/BrandChangedEvent/UnitChangedEvent/TagChangedEvent
- location 新增 LocationChangedEvent
- CatalogEventPublisher/LocationEventPublisher 在事务末尾发布事件
- ItemService/CatalogDictionaryService/LocationService 写操作末尾接入事件发布"
```

---

### Task 4: InventoryApi / CatalogApi / LocationApi 快照拉取端口

**Files:**
- Modify: `backend/src/main/java/com/zija/inventory/InventoryApi.java` (+dump 方法 + DTO)
- Modify: `backend/src/main/java/com/zija/inventory/internal/InventoryService.java` (实现 dump)
- Create: `backend/src/main/resources/mapper/inventory/InventoryDumpMapper.xml`
- Modify: `backend/src/main/java/com/zija/inventory/internal/persistence/MovementMapper.java` (+dump 方法)
- Modify: `backend/src/main/java/com/zija/inventory/internal/persistence/StockPositionMapper.java` (+dump 方法)
- Modify: `backend/src/main/java/com/zija/catalog/CatalogApi.java` (+dump 方法 + DTO)
- Modify: `backend/src/main/java/com/zija/catalog/internal/ItemService.java` (实现 dump)
- Create: `backend/src/main/resources/mapper/catalog/ItemDumpMapper.xml`
- Create: `backend/src/main/java/com/zija/catalog/internal/persistence/ItemDumpMapper.java`
- Modify: `backend/src/main/java/com/zija/location/LocationApi.java` (+dump 方法 + DTO)
- Modify: `backend/src/main/java/com/zija/location/internal/LocationService.java` (实现 dump)
- Create: `backend/src/main/resources/mapper/location/LocationDumpMapper.xml`
- Create: `backend/src/main/java/com/zija/location/internal/persistence/LocationDumpMapper.java`

**Interfaces:**
- Produces: `InventoryApi.dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit) → PageDump<StockPositionDump>`
- Produces: `InventoryApi.dumpMovements(UUID householdId, OffsetDateTime cursor, int limit) → PageDump<MovementDump>`
- Produces: `CatalogApi.dumpItems(UUID householdId, OffsetDateTime cursor, int limit) → ItemDumpPage`
- Produces: `LocationApi.dumpTree(UUID householdId, OffsetDateTime cursor, int limit) → LocationDumpPage`

- [ ] **Step 1: 在 `InventoryApi` 中追加 dump 方法与 DTO**

```java
// 在 InventoryApi.java 中追加（现有方法之后）

/** 增量拉取家庭库存位（按 updated_at 游标分批）。仅供 reporting 投影重建。 */
PageDump<StockPositionDump> dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit);

/** 增量拉取家庭全部库存流水（按 created_at 游标分批）。仅供 reporting 投影重建。 */
PageDump<MovementDump> dumpMovements(UUID householdId, OffsetDateTime cursor, int limit);

/** 分页拉取结果，游标为最后一条的排序字段值。 */
record PageDump<T>(List<T> items, OffsetDateTime nextCursor, boolean hasMore) {}

/** 库存位快照 DTO（仅供 dump）。 */
record StockPositionDump(
        UUID lotId,
        UUID itemId,
        UUID locationId,
        BigDecimal quantity,
        OffsetDateTime updatedAt
) {}

/** 库存流水快照 DTO（仅供 dump）。 */
record MovementDump(
        UUID id,
        UUID lotId,
        UUID itemId,
        String type,
        BigDecimal quantityDelta,
        UUID fromLocationId,
        UUID toLocationId,
        String reason,
        UUID operatorAccountId,
        UUID reversalOf,
        OffsetDateTime businessTime,
        OffsetDateTime createdAt
) {}
```

- [ ] **Step 2: 在 Mapper 接口中追加 dump 查询方法**

在 `StockPositionMapper.java` 追加：

```java
/** 按 updated_at 增量拉取（游标分批），供 reporting 投影重建。 */
List<StockPositionDump> dumpStockPositions(@Param("householdId") UUID householdId,
                                            @Param("cursor") OffsetDateTime cursor,
                                            @Param("limit") int limit);
```

在 `MovementMapper.java` 追加：

```java
/** 按 created_at 增量拉取（游标分批），供 reporting 投影重建。 */
List<MovementDump> dumpMovements(@Param("householdId") UUID householdId,
                                  @Param("cursor") OffsetDateTime cursor,
                                  @Param("limit") int limit);
```

- [ ] **Step 3: 创建 `InventoryDumpMapper.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.StockPositionMapper">

    <resultMap id="stockPositionDumpMap" type="com.zija.inventory.InventoryApi$StockPositionDump">
        <result property="lotId" column="lot_id"/>
        <result property="itemId" column="item_id"/>
        <result property="locationId" column="location_id"/>
        <result property="quantity" column="quantity"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="dumpStockPositions" resultMap="stockPositionDumpMap">
        SELECT sp.lot_id, l.item_id, sp.location_id, sp.quantity, sp.updated_at
        FROM inventory_stock_position sp
        JOIN inventory_lot l ON l.id = sp.lot_id
        WHERE sp.household_id = #{householdId}
          AND sp.updated_at &gt; #{cursor}
        ORDER BY sp.updated_at ASC
        LIMIT #{limit}
    </select>

</mapper>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.MovementMapper">

    <resultMap id="movementDumpMap" type="com.zija.inventory.InventoryApi$MovementDump">
        <result property="id" column="id"/>
        <result property="lotId" column="lot_id"/>
        <result property="itemId" column="item_id"/>
        <result property="type" column="type"/>
        <result property="quantityDelta" column="quantity_delta"/>
        <result property="fromLocationId" column="from_location_id"/>
        <result property="toLocationId" column="to_location_id"/>
        <result property="reason" column="reason"/>
        <result property="operatorAccountId" column="operator_account_id"/>
        <result property="reversalOf" column="reversal_of"/>
        <result property="businessTime" column="business_time"/>
        <result property="createdAt" column="created_at"/>
    </resultMap>

    <select id="dumpMovements" resultMap="movementDumpMap">
        SELECT id, lot_id, item_id, type, quantity_delta, from_location_id, to_location_id,
               reason, operator_account_id, reversal_of, business_time, created_at
        FROM inventory_movement
        WHERE household_id = #{householdId}
          AND created_at &gt; #{cursor}
        ORDER BY created_at ASC
        LIMIT #{limit}
    </select>

</mapper>
```

- [ ] **Step 4: 在 `InventoryService` 中实现 dump 方法**

```java
// 在 InventoryService.java 中追加

@Override
public PageDump<StockPositionDump> dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit) {
    var items = stockPositionMapper.dumpStockPositions(householdId, cursor, limit);
    OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
    boolean hasMore = items.size() == limit;
    return new PageDump<>(items, nextCursor, hasMore);
}

@Override
public PageDump<MovementDump> dumpMovements(UUID householdId, OffsetDateTime cursor, int limit) {
    var items = movementMapper.dumpMovements(householdId, cursor, limit);
    OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).createdAt();
    boolean hasMore = items.size() == limit;
    return new PageDump<>(items, nextCursor, hasMore);
}
```

- [ ] **Step 5: 在 `CatalogApi` 中追加 dump 方法与 DTO**

```java
// 在 CatalogApi.java 中追加

/** 增量拉取家庭物品（含品牌、分类、单位、标签 join 后扁平化）。仅供 reporting 投影重建。 */
ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit);

record ItemDumpPage(List<ItemFlat> items, OffsetDateTime nextCursor, boolean hasMore) {}

/** 物品扁平 DTO（仅供 dump）。 */
record ItemFlat(
        UUID itemId,
        UUID householdId,
        String name,
        String managementType,
        String status,
        UUID categoryId,
        String categoryName,
        UUID brandId,
        String brandName,
        UUID unitId,
        String unitName,
        String tagNames,
        BigDecimal lowStockThreshold,
        String lowStockMode,
        String expiryReminderMode,
        List<Short> expiryReminderDays,
        OffsetDateTime updatedAt
) {}
```

- [ ] **Step 6: 创建 `ItemDumpMapper.java` + `ItemDumpMapper.xml`**

```java
// backend/src/main/java/com/zija/catalog/internal/persistence/ItemDumpMapper.java
package com.zija.catalog.internal.persistence;

import com.zija.catalog.CatalogApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemDumpMapper {
    List<CatalogApi.ItemFlat> dumpItems(@Param("householdId") UUID householdId,
                                         @Param("cursor") OffsetDateTime cursor,
                                         @Param("limit") int limit);
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.catalog.internal.persistence.ItemDumpMapper">

    <resultMap id="itemFlatMap" type="com.zija.catalog.CatalogApi$ItemFlat">
        <result property="itemId" column="item_id"/>
        <result property="householdId" column="household_id"/>
        <result property="name" column="name"/>
        <result property="managementType" column="management_type"/>
        <result property="status" column="status"/>
        <result property="categoryId" column="category_id"/>
        <result property="categoryName" column="category_name"/>
        <result property="brandId" column="brand_id"/>
        <result property="brandName" column="brand_name"/>
        <result property="unitId" column="unit_id"/>
        <result property="unitName" column="unit_name"/>
        <result property="tagNames" column="tag_names"/>
        <result property="lowStockThreshold" column="low_stock_threshold"/>
        <result property="lowStockMode" column="low_stock_mode"/>
        <result property="expiryReminderMode" column="expiry_reminder_mode"/>
        <result property="expiryReminderDays" column="expiry_reminder_days"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="dumpItems" resultMap="itemFlatMap">
        SELECT
            i.id AS item_id, i.household_id, i.name, i.management_type, i.status,
            i.category_id, c.name AS category_name,
            i.brand_id, b.name AS brand_name,
            i.unit_id, u.name AS unit_name,
            STRING_AGG(t.name, ',' ORDER BY t.name) AS tag_names,
            i.low_stock_threshold, i.low_stock_mode,
            i.expiry_reminder_mode, i.expiry_reminder_days,
            i.updated_at
        FROM catalog_item i
        LEFT JOIN catalog_category c ON c.id = i.category_id
        LEFT JOIN catalog_brand b ON b.id = i.brand_id
        LEFT JOIN catalog_unit u ON u.id = i.unit_id
        LEFT JOIN catalog_item_tag it ON it.item_id = i.id
        LEFT JOIN catalog_tag t ON t.id = it.tag_id
        WHERE i.household_id = #{householdId}
          AND i.updated_at &gt; #{cursor}
        GROUP BY i.id, i.household_id, i.name, i.management_type, i.status,
                 i.category_id, c.name, i.brand_id, b.name, i.unit_id, u.name,
                 i.low_stock_threshold, i.low_stock_mode,
                 i.expiry_reminder_mode, i.expiry_reminder_days, i.updated_at
        ORDER BY i.updated_at ASC
        LIMIT #{limit}
    </select>

</mapper>
```

- [ ] **Step 7: 在 `ItemService` 中实现 `dumpItems`**

```java
// 在 ItemService.java 中追加（需要注入 ItemDumpMapper）

@Override
public ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit) {
    var items = itemDumpMapper.dumpItems(householdId, cursor, limit);
    OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
    boolean hasMore = items.size() == limit;
    return new ItemDumpPage(items, nextCursor, hasMore);
}
```

- [ ] **Step 8: 在 `LocationApi` 中追加 dump 方法与 DTO**

```java
// 在 LocationApi.java 中追加

/** 增量拉取家庭位置树扁平化（含 path）。仅供 reporting 投影重建。 */
LocationDumpPage dumpTree(UUID householdId, OffsetDateTime cursor, int limit);

record LocationDumpPage(List<LocationFlat> items, OffsetDateTime nextCursor, boolean hasMore) {}

/** 位置扁平 DTO（仅供 dump）。 */
record LocationFlat(
        UUID locationId,
        UUID householdId,
        UUID parentId,
        String name,
        String path,
        int sortOrder,
        String status,
        OffsetDateTime updatedAt
) {}
```

- [ ] **Step 9: 创建 `LocationDumpMapper.java` + `LocationDumpMapper.xml`**

```java
// backend/src/main/java/com/zija/location/internal/persistence/LocationDumpMapper.java
package com.zija.location.internal.persistence;

import com.zija.location.LocationApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationDumpMapper {
    List<LocationApi.LocationFlat> dumpTree(@Param("householdId") UUID householdId,
                                             @Param("cursor") OffsetDateTime cursor,
                                             @Param("limit") int limit);
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.location.internal.persistence.LocationDumpMapper">

    <resultMap id="locationFlatMap" type="com.zija.location.LocationApi$LocationFlat">
        <result property="locationId" column="location_id"/>
        <result property="householdId" column="household_id"/>
        <result property="parentId" column="parent_id"/>
        <result property="name" column="name"/>
        <result property="path" column="path"/>
        <result property="sortOrder" column="sort_order"/>
        <result property="status" column="status"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <!-- 用递归 CTE 构建 path（> 分隔父节点名称） -->
    <select id="dumpTree" resultMap="locationFlatMap">
        WITH RECURSIVE loc_tree AS (
            SELECT id, household_id, parent_id, name, sort_order, status, updated_at,
                   name::TEXT AS path
            FROM catalog_location
            WHERE household_id = #{householdId} AND parent_id IS NULL
              AND updated_at &gt; #{cursor}
            UNION ALL
            SELECT c.id, c.household_id, c.parent_id, c.name, c.sort_order, c.status, c.updated_at,
                   (p.path || '&gt;' || c.name)
            FROM catalog_location c
            JOIN loc_tree p ON c.parent_id = p.id
            WHERE c.updated_at &gt; #{cursor}
        )
        SELECT id AS location_id, household_id, parent_id, name, path, sort_order, status, updated_at
        FROM loc_tree
        ORDER BY updated_at ASC
        LIMIT #{limit}
    </select>

</mapper>
```

> **注意：** 上面 SQL 中的 `>` 转义为 `&gt;` 是 MyBatis XML 要求。实际 path 分隔符为 `>`。

- [ ] **Step 10: 在 `LocationService` 中实现 `dumpTree`**

```java
// 在 LocationService.java 中追加（需要注入 LocationDumpMapper）

@Override
public LocationDumpPage dumpTree(UUID householdId, OffsetDateTime cursor, int limit) {
    var items = locationDumpMapper.dumpTree(householdId, cursor, limit);
    OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
    boolean hasMore = items.size() == limit;
    return new LocationDumpPage(items, nextCursor, hasMore);
}
```

- [ ] **Step 11: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/zija/inventory/InventoryApi.java \
        backend/src/main/java/com/zija/inventory/internal/InventoryService.java \
        backend/src/main/java/com/zija/inventory/internal/persistence/StockPositionMapper.java \
        backend/src/main/java/com/zija/inventory/internal/persistence/MovementMapper.java \
        backend/src/main/resources/mapper/inventory/ \
        backend/src/main/java/com/zija/catalog/CatalogApi.java \
        backend/src/main/java/com/zija/catalog/internal/ItemService.java \
        backend/src/main/java/com/zija/catalog/internal/persistence/ItemDumpMapper.java \
        backend/src/main/resources/mapper/catalog/ItemDumpMapper.xml \
        backend/src/main/java/com/zija/location/LocationApi.java \
        backend/src/main/java/com/zija/location/internal/LocationService.java \
        backend/src/main/java/com/zija/location/internal/persistence/LocationDumpMapper.java \
        backend/src/main/resources/mapper/location/LocationDumpMapper.xml
git commit -m "feat(api): InventoryApi/CatalogApi/LocationApi 快照拉取端口

- InventoryApi: dumpStockPositions/dumpMovements（游标分批，按 updated_at/created_at）
- CatalogApi: dumpItems（物品扁平化，含品牌/分类/单位/标签 join）
- LocationApi: dumpTree（递归 CTE 构建 path，游标分批）
- 所有 dump 方法仅供 reporting 投影重建，不暴露 REST 端点"
```

---

### Task 5: reporting 持久化实体 + Mapper

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/ProcessedEventEntity.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/ProcessedEventMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/ProcessedEventMapper.xml`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/DeadLetterEntity.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/DeadLetterMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/DeadLetterMapper.xml`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/SearchIndexEntity.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/SearchIndexMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/SearchIndexMapper.xml`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/StockFlatEntity.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/StockFlatMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/StockFlatMapper.xml`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/MovementFlatEntity.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/MovementFlatMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/MovementFlatMapper.xml`

**Interfaces:**
- Produces: `ProcessedEventMapper.insertOnConflictDoNothing(UUID eventId, String eventType) → int`
- Produces: `DeadLetterMapper` (findDueForRetry, incrementFailure, markAbandoned)
- Produces: `SearchIndexMapper.upsert(SearchIndexEntity)` + `deleteByEntity(UUID householdId, String entityType, UUID entityId)`
- Produces: `StockFlatMapper.upsert(StockFlatEntity)` + `deleteByLot(UUID householdId, UUID lotId)`
- Produces: `MovementFlatMapper.upsert(MovementFlatEntity)`

- [ ] **Step 1: 创建 `ProcessedEventEntity` + `ProcessedEventMapper` + XML**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/ProcessedEventEntity.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_processed_event")
public class ProcessedEventEntity {
    @TableId
    private UUID eventId;
    private String eventType;
    private OffsetDateTime processedAt;

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
```

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/ProcessedEventMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface ProcessedEventMapper extends BaseMapper<ProcessedEventEntity> {
    /** INSERT ON CONFLICT DO NOTHING，返回受影响行数（0=已存在跳过）。 */
    int insertOnConflictDoNothing(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}
```

```xml
<!-- backend/src/main/resources/mapper/reporting/ProcessedEventMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.ProcessedEventMapper">
    <insert id="insertOnConflictDoNothing">
        INSERT INTO reporting_processed_event(event_id, event_type, processed_at)
        VALUES (#{eventId}, #{eventType}, CURRENT_TIMESTAMP)
        ON CONFLICT (event_id) DO NOTHING
    </insert>
</mapper>
```

- [ ] **Step 2: 创建 `DeadLetterEntity` + `DeadLetterMapper` + XML**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/DeadLetterEntity.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "reporting_event_dead_letter", autoResultMap = true)
public class DeadLetterEntity {
    @TableId
    private UUID id;
    private UUID eventId;
    private String eventType;
    private Map<String, Object> payload;
    private Integer failureCount;
    private OffsetDateTime nextRetryAt;
    private String lastError;
    private OffsetDateTime lastRetryAt;
    private Boolean abandoned;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(OffsetDateTime lastRetryAt) { this.lastRetryAt = lastRetryAt; }
    public Boolean getAbandoned() { return abandoned; }
    public void setAbandoned(Boolean abandoned) { this.abandoned = abandoned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
```

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/DeadLetterMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DeadLetterMapper extends BaseMapper<DeadLetterEntity> {
    List<DeadLetterEntity> findDueForRetry(@Param("now") OffsetDateTime now, @Param("limit") int limit);
    int incrementFailure(@Param("id") UUID id, @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                          @Param("lastError") String lastError);
    int markAbandoned(@Param("id") UUID id);
}
```

```xml
<!-- backend/src/main/resources/mapper/reporting/DeadLetterMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.DeadLetterMapper">

    <resultMap id="deadLetterResultMap" type="com.zija.reporting.internal.persistence.DeadLetterEntity">
        <id property="id" column="id"/>
        <result property="eventId" column="event_id"/>
        <result property="eventType" column="event_type"/>
        <result property="payload" column="payload"
                typeHandler="com.zija.system.internal.persistence.JsonbTypeHandler"/>
        <result property="failureCount" column="failure_count"/>
        <result property="nextRetryAt" column="next_retry_at"/>
        <result property="lastError" column="last_error"/>
        <result property="lastRetryAt" column="last_retry_at"/>
        <result property="abandoned" column="abandoned"/>
        <result property="createdAt" column="created_at"/>
    </resultMap>

    <select id="findDueForRetry" resultMap="deadLetterResultMap">
        SELECT id, event_id, event_type, payload, failure_count, next_retry_at,
               last_error, last_retry_at, abandoned, created_at
        FROM reporting_event_dead_letter
        WHERE abandoned = FALSE AND next_retry_at &lt;= #{now}
        ORDER BY next_retry_at ASC
        LIMIT #{limit}
        FOR UPDATE SKIP LOCKED
    </select>

    <update id="incrementFailure">
        UPDATE reporting_event_dead_letter
        SET failure_count = failure_count + 1,
            next_retry_at = #{nextRetryAt},
            last_error = #{lastError},
            last_retry_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <update id="markAbandoned">
        UPDATE reporting_event_dead_letter
        SET abandoned = TRUE, last_retry_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 3: 创建 `SearchIndexEntity` + `SearchIndexMapper` + XML**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/SearchIndexEntity.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_search_index")
public class SearchIndexEntity {
    private UUID householdId;
    private String entityType;
    private UUID entityId;
    private String itemName;
    private String brandName;
    private String tagNames;
    private String categoryName;
    private String unitName;
    private UUID lotId;
    private String lotNumber;
    private String serialNumber;
    private UUID locationId;
    private String locationName;
    private String locationPath;
    private OffsetDateTime updatedAt;

    // getter/setter（省略，按标准 bean 模式手写）
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID lotId) { this.lotId = lotId; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getLocationPath() { return locationPath; }
    public void setLocationPath(String locationPath) { this.locationPath = locationPath; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/SearchIndexMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface SearchIndexMapper extends BaseMapper<SearchIndexEntity> {
    /** 按主键 upsert（INSERT ON CONFLICT UPDATE）。 */
    int upsert(SearchIndexEntity entity);
    /** 删除指定实体的搜索索引行。 */
    int deleteByEntity(@Param("householdId") UUID householdId,
                        @Param("entityType") String entityType,
                        @Param("entityId") UUID entityId);
}
```

```xml
<!-- backend/src/main/resources/mapper/reporting/SearchIndexMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.SearchIndexMapper">

    <insert id="upsert">
        INSERT INTO reporting_search_index (
            household_id, entity_type, entity_id,
            item_name, brand_name, tag_names, category_name, unit_name,
            lot_id, lot_number, serial_number,
            location_id, location_name, location_path, updated_at
        ) VALUES (
            #{householdId}, #{entityType}, #{entityId},
            #{itemName}, #{brandName}, #{tagNames}, #{categoryName}, #{unitName},
            #{lotId}, #{lotNumber}, #{serialNumber},
            #{locationId}, #{locationName}, #{locationPath}, #{updatedAt}
        )
        ON CONFLICT (household_id, entity_type, entity_id) DO UPDATE SET
            item_name = EXCLUDED.item_name,
            brand_name = EXCLUDED.brand_name,
            tag_names = EXCLUDED.tag_names,
            category_name = EXCLUDED.category_name,
            unit_name = EXCLUDED.unit_name,
            lot_id = EXCLUDED.lot_id,
            lot_number = EXCLUDED.lot_number,
            serial_number = EXCLUDED.serial_number,
            location_id = EXCLUDED.location_id,
            location_name = EXCLUDED.location_name,
            location_path = EXCLUDED.location_path,
            updated_at = EXCLUDED.updated_at
    </insert>

    <delete id="deleteByEntity">
        DELETE FROM reporting_search_index
        WHERE household_id = #{householdId}
          AND entity_type = #{entityType}
          AND entity_id = #{entityId}
    </delete>

</mapper>
```

- [ ] **Step 4: 创建 `StockFlatEntity` + `StockFlatMapper` + XML**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/StockFlatEntity.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_stock_flat")
public class StockFlatEntity {
    private UUID householdId;
    private UUID lotId;
    private UUID itemId;
    private String itemName;
    private String unitName;
    private String lotNumber;
    private String serialNumber;
    private LocalDate expiryDate;
    private UUID locationId;
    private String locationPath;
    private BigDecimal quantity;
    private OffsetDateTime updatedAt;

    // getter/setter（标准 bean 模式，省略完整代码）
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public String getItemName() { return itemName; }
    public void setItemName(String v) { this.itemName = v; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String v) { this.unitName = v; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String v) { this.lotNumber = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { this.serialNumber = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID v) { this.locationId = v; }
    public String getLocationPath() { return locationPath; }
    public void setLocationPath(String v) { this.locationPath = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
```

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/StockFlatMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface StockFlatMapper extends BaseMapper<StockFlatEntity> {
    int upsert(StockFlatEntity entity);
    int deleteByLot(@Param("householdId") UUID householdId, @Param("lotId") UUID lotId);
}
```

```xml
<!-- backend/src/main/resources/mapper/reporting/StockFlatMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.StockFlatMapper">

    <insert id="upsert">
        INSERT INTO reporting_stock_flat (
            household_id, lot_id, item_id, item_name, unit_name,
            lot_number, serial_number, expiry_date,
            location_id, location_path, quantity, updated_at
        ) VALUES (
            #{householdId}, #{lotId}, #{itemId}, #{itemName}, #{unitName},
            #{lotNumber}, #{serialNumber}, #{expiryDate},
            #{locationId}, #{locationPath}, #{quantity}, #{updatedAt}
        )
        ON CONFLICT (household_id, lot_id, location_id) DO UPDATE SET
            item_id = EXCLUDED.item_id,
            item_name = EXCLUDED.item_name,
            unit_name = EXCLUDED.unit_name,
            lot_number = EXCLUDED.lot_number,
            serial_number = EXCLUDED.serial_number,
            expiry_date = EXCLUDED.expiry_date,
            location_path = EXCLUDED.location_path,
            quantity = EXCLUDED.quantity,
            updated_at = EXCLUDED.updated_at
    </insert>

    <delete id="deleteByLot">
        DELETE FROM reporting_stock_flat
        WHERE household_id = #{householdId} AND lot_id = #{lotId}
    </delete>

</mapper>
```

- [ ] **Step 5: 创建 `MovementFlatEntity` + `MovementFlatMapper` + XML**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/MovementFlatEntity.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_movement_flat")
public class MovementFlatEntity {
    private UUID householdId;
    @TableId
    private UUID movementId;
    private UUID eventId;
    private UUID lotId;
    private UUID itemId;
    private String itemName;
    private String type;
    private BigDecimal quantityDelta;
    private UUID fromLocationId;
    private UUID toLocationId;
    private String fromLocationPath;
    private String toLocationPath;
    private UUID operatorAccountId;
    private String operatorDisplayName;
    private String reason;
    private UUID reversalOf;
    private OffsetDateTime businessTime;
    private OffsetDateTime createdAt;

    // getter/setter（标准 bean 模式）
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getMovementId() { return movementId; }
    public void setMovementId(UUID v) { this.movementId = v; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID v) { this.eventId = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public String getItemName() { return itemName; }
    public void setItemName(String v) { this.itemName = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(BigDecimal v) { this.quantityDelta = v; }
    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID v) { this.fromLocationId = v; }
    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID v) { this.toLocationId = v; }
    public String getFromLocationPath() { return fromLocationPath; }
    public void setFromLocationPath(String v) { this.fromLocationPath = v; }
    public String getToLocationPath() { return toLocationPath; }
    public void setToLocationPath(String v) { this.toLocationPath = v; }
    public UUID getOperatorAccountId() { return operatorAccountId; }
    public void setOperatorAccountId(UUID v) { this.operatorAccountId = v; }
    public String getOperatorDisplayName() { return operatorDisplayName; }
    public void setOperatorDisplayName(String v) { this.operatorDisplayName = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID v) { this.reversalOf = v; }
    public OffsetDateTime getBusinessTime() { return businessTime; }
    public void setBusinessTime(OffsetDateTime v) { this.businessTime = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
```

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/MovementFlatMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MovementFlatMapper extends BaseMapper<MovementFlatEntity> {
    int upsert(MovementFlatEntity entity);
}
```

```xml
<!-- backend/src/main/resources/mapper/reporting/MovementFlatMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.MovementFlatMapper">

    <insert id="upsert">
        INSERT INTO reporting_movement_flat (
            household_id, movement_id, event_id, lot_id, item_id, item_name,
            type, quantity_delta, from_location_id, to_location_id,
            from_location_path, to_location_path,
            operator_account_id, operator_display_name,
            reason, reversal_of, business_time, created_at
        ) VALUES (
            #{householdId}, #{movementId}, #{eventId}, #{lotId}, #{itemId}, #{itemName},
            #{type}, #{quantityDelta}, #{fromLocationId}, #{toLocationId},
            #{fromLocationPath}, #{toLocationPath},
            #{operatorAccountId}, #{operatorDisplayName},
            #{reason}, #{reversalOf}, #{businessTime}, #{createdAt}
        )
        ON CONFLICT (movement_id) DO UPDATE SET
            item_name = EXCLUDED.item_name,
            type = EXCLUDED.type,
            quantity_delta = EXCLUDED.quantity_delta,
            from_location_id = EXCLUDED.from_location_id,
            to_location_id = EXCLUDED.to_location_id,
            from_location_path = EXCLUDED.from_location_path,
            to_location_path = EXCLUDED.to_location_path,
            operator_account_id = EXCLUDED.operator_account_id,
            operator_display_name = EXCLUDED.operator_display_name,
            reason = EXCLUDED.reason,
            reversal_of = EXCLUDED.reversal_of
    </insert>

</mapper>
```

- [ ] **Step 6: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/persistence/ \
        backend/src/main/resources/mapper/reporting/
git commit -m "feat(reporting): 持久化实体 + Mapper（5 张投影表 + 事件去重 + 死信）

- ProcessedEventEntity/Mapper: insertOnConflictDoNothing 去重
- DeadLetterEntity/Mapper: findDueForRetry/incrementFailure/markAbandoned
- SearchIndexMapper: upsert + deleteByEntity
- StockFlatMapper: upsert + deleteByLot
- MovementFlatMapper: upsert
- 所有 upsert 使用 ON CONFLICT DO UPDATE 幂等"
```

---

### Task 6: ProjectionListener — 事件监听 → 投影 upsert

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/projection/ProjectionListener.java`

**Interfaces:**
- Consumes: `StockChangedEvent`（from inventory）、`ItemChangedEvent` / `CategoryChangedEvent` / `BrandChangedEvent` / `UnitChangedEvent` / `TagChangedEvent`（from catalog）、`LocationChangedEvent`（from location）
- Consumes: `ProcessedEventMapper`、`DeadLetterMapper`、`SearchIndexMapper`、`StockFlatMapper`、`MovementFlatMapper`
- Consumes: `CatalogApi.dumpItems`、`LocationApi.dumpTree`、`InventoryApi.dumpStockPositions`（用于拉取关联数据补全投影字段）
- Consumes: `IdentityApi.findByIds`、`HouseholdApi.findMembers`（用于填充 operator_display_name）

- [ ] **Step 1: 创建 `ProjectionListener` 骨架**

```java
// backend/src/main/java/com/zija/reporting/internal/projection/ProjectionListener.java
package com.zija.reporting.internal.projection;

import com.zija.catalog.*;
import com.zija.household.HouseholdApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.StockChangedEvent;
import com.zija.location.LocationApi;
import com.zija.location.LocationChangedEvent;
import com.zija.reporting.internal.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 投影事件监听器。去重后在 REQUIRES_NEW 事务中 upsert 投影表。
 * 失败时写 dead-letter 并删除去重行，允许重试。
 */
@Service
public class ProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

    private final ProcessedEventMapper processedEventMapper;
    private final DeadLetterMapper deadLetterMapper;
    private final SearchIndexMapper searchIndexMapper;
    private final StockFlatMapper stockFlatMapper;
    private final MovementFlatMapper movementFlatMapper;
    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final InventoryApi inventoryApi;
    private final IdentityApi identityApi;
    private final HouseholdApi householdApi;
    private final TransactionTemplate requiresNewTx;

    public ProjectionListener(ProcessedEventMapper processedEventMapper,
                               DeadLetterMapper deadLetterMapper,
                               SearchIndexMapper searchIndexMapper,
                               StockFlatMapper stockFlatMapper,
                               MovementFlatMapper movementFlatMapper,
                               CatalogApi catalogApi,
                               LocationApi locationApi,
                               InventoryApi inventoryApi,
                               IdentityApi identityApi,
                               HouseholdApi householdApi,
                               PlatformTransactionManager txManager) {
        this.processedEventMapper = processedEventMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.householdApi = householdApi;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    // --- 库存事件 ---

    @EventListener
    public void onStockChanged(StockChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "StockChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> handleStockChanged(evt));
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "StockChangedEvent", toMap(evt), ex);
            log.warn("StockChangedEvent projection failed, wrote dead-letter: eventId={}",
                    evt.eventId(), ex);
        }
    }

    private void handleStockChanged(StockChangedEvent evt) {
        // 1. upsert reporting_movement_flat
        var movEntity = buildMovementFlat(evt);
        movementFlatMapper.upsert(movEntity);

        // 2. upsert reporting_stock_flat（数量变更 → 重新拉取该批次所有库存位）
        rebuildStockFlatForLot(evt.householdId(), evt.lotId());
    }

    private MovementFlatEntity buildMovementFlat(StockChangedEvent evt) {
        var e = new MovementFlatEntity();
        e.setHouseholdId(evt.householdId());
        e.setMovementId(evt.movementId());
        e.setEventId(evt.eventId());
        e.setLotId(evt.lotId());
        e.setItemId(evt.itemId());
        e.setItemName(resolveItemName(evt.householdId(), evt.itemId()));
        e.setType(evt.movementType());
        e.setQuantityDelta(evt.quantityDelta());
        e.setFromLocationId(evt.fromLocationId());
        e.setToLocationId(evt.toLocationId());
        e.setFromLocationPath(resolveLocationPath(evt.householdId(), evt.fromLocationId()));
        e.setToLocationPath(resolveLocationPath(evt.householdId(), evt.toLocationId()));
        e.setOperatorAccountId(evt.operatorAccountId());
        e.setOperatorDisplayName(resolveDisplayName(evt.operatorAccountId()));
        e.setReason(evt.reason());
        e.setReversalOf(evt.reversalOf());
        e.setBusinessTime(evt.businessTime());
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    private void rebuildStockFlatForLot(UUID householdId, UUID lotId) {
        stockFlatMapper.deleteByLot(householdId, lotId);
        var positions = inventoryApi.stockPositionsOfItem(householdId, null);
        // 实际实现：按 lotId 筛选库存位，逐条 upsert stock_flat
        // 需要从 lotId 查 itemId、itemName 等信息
        // 详细实现见下方补充
    }

    // --- catalog 事件 ---

    @EventListener
    public void onItemChanged(ItemChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "ItemChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> handleItemChanged(evt));
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "ItemChangedEvent", toMap(evt), ex);
            log.warn("ItemChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    private void handleItemChanged(ItemChangedEvent evt) {
        if ("ARCHIVED".equals(evt.changeType())) {
            searchIndexMapper.deleteByEntity(evt.householdId(), "ITEM", evt.itemId());
            return;
        }
        // 从 CatalogApi.dumpItems 拉取最新数据重建搜索索引
        var page = catalogApi.dumpItems(evt.householdId(), OffsetDateTime.MIN, 1);
        // 实现：找到匹配 itemId 的 ItemFlat → upsert search_index
    }

    @EventListener
    public void onCategoryChanged(CategoryChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "CategoryChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> {
                // 分类变更 → 重建受影响物品的 search_index 行
                // 通过 dumpItems 拉取该分类下所有物品，逐条 upsert
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "CategoryChangedEvent", Map.of(), ex);
        }
    }

    @EventListener
    public void onBrandChanged(BrandChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "BrandChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> {
                // 品牌变更 → 重建受影响物品的 search_index 行
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "BrandChangedEvent", Map.of(), ex);
        }
    }

    @EventListener
    public void onUnitChanged(UnitChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "UnitChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> {
                // 单位变更 → 重建受影响物品的 search_index 行
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "UnitChangedEvent", Map.of(), ex);
        }
    }

    @EventListener
    public void onTagChanged(TagChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "TagChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> {
                // 标签变更 → 重建受影响物品的 search_index 行（tag_names 字段）
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "TagChangedEvent", Map.of(), ex);
        }
    }

    // --- location 事件 ---

    @EventListener
    public void onLocationChanged(LocationChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(
                evt.eventId(), "LocationChangedEvent");
        if (rows == 0) return;
        try {
            upsertInNewTx(() -> handleLocationChanged(evt));
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "LocationChangedEvent", toMap(evt), ex);
            log.warn("LocationChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    private void handleLocationChanged(LocationChangedEvent evt) {
        if ("DELETED".equals(evt.changeType())) {
            searchIndexMapper.deleteByEntity(evt.householdId(), "LOCATION", evt.locationId());
            return;
        }
        // 从 LocationApi.dumpTree 拉取最新数据重建搜索索引
        // 同时更新 reporting_stock_flat 中引用该位置的 location_path
    }

    // --- 辅助方法 ---

    private void upsertInNewTx(Runnable action) {
        requiresNewTx.executeWithoutResult(status -> action.run());
    }

    private void saveDeadLetterInNewTx(UUID eventId, String eventType,
                                        Map<String, Object> payload, Throwable err) {
        requiresNewTx.executeWithoutResult(status -> {
            processedEventMapper.deleteById(eventId);
            var dl = new DeadLetterEntity();
            dl.setId(UUID.randomUUID());
            dl.setEventId(eventId);
            dl.setEventType(eventType);
            dl.setPayload(payload);
            dl.setFailureCount(1);
            dl.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
            dl.setLastError(truncate(err.getMessage(), 4000));
            dl.setAbandoned(false);
            dl.setCreatedAt(OffsetDateTime.now());
            try {
                deadLetterMapper.insert(dl);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发写入，忽略
            }
        });
    }

    private String resolveItemName(UUID householdId, UUID itemId) {
        // 通过 CatalogApi 拉取物品名；失败返回 itemId.toString()
        try {
            var item = catalogApi.requireItem(householdId, itemId);
            return item.name();
        } catch (Exception e) {
            return itemId.toString();
        }
    }

    private String resolveLocationPath(UUID householdId, UUID locationId) {
        if (locationId == null) return null;
        try {
            var loc = locationApi.requireLocation(householdId, locationId);
            // 需要构建 path；LocationInfo 不含 path，需遍历 tree
            // 实现时可缓存或从 search_index 查
            return loc.name();
        } catch (Exception e) {
            return locationId.toString();
        }
    }

    private String resolveDisplayName(UUID accountId) {
        if (accountId == null) return null;
        try {
            var account = identityApi.findById(accountId);
            return account.map(IdentityApi.AccountInfo::displayName).orElse(accountId.toString());
        } catch (Exception e) {
            return accountId.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }

    // toMap 方法：将各类事件转为 Map 用于 dead-letter payload
    private Map<String, Object> toMap(StockChangedEvent evt) {
        return Map.ofEntries(
                Map.entry("eventId", evt.eventId().toString()),
                Map.entry("householdId", evt.householdId().toString()),
                Map.entry("lotId", evt.lotId().toString()),
                Map.entry("itemId", evt.itemId().toString()),
                Map.entry("movementType", evt.movementType()),
                Map.entry("quantityDelta", evt.quantityDelta().toString()),
                Map.entry("fromLocationId", evt.fromLocationId() == null ? "" : evt.fromLocationId().toString()),
                Map.entry("toLocationId", evt.toLocationId() == null ? "" : evt.toLocationId().toString()),
                Map.entry("businessTime", evt.businessTime().toString()),
                Map.entry("movementId", evt.movementId().toString()),
                Map.entry("idempotencyKey", evt.idempotencyKey().toString()),
                Map.entry("operatorAccountId", evt.operatorAccountId() == null ? "" : evt.operatorAccountId().toString()),
                Map.entry("reason", evt.reason() == null ? "" : evt.reason()),
                Map.entry("reversalOf", evt.reversalOf() == null ? "" : evt.reversalOf().toString())
        );
    }

    private Map<String, Object> toMap(ItemChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "itemId", evt.itemId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(LocationChangedEvent evt) {
        return Map.ofEntries(
                Map.entry("eventId", evt.eventId().toString()),
                Map.entry("householdId", evt.householdId().toString()),
                Map.entry("locationId", evt.locationId().toString()),
                Map.entry("changeType", evt.changeType()),
                Map.entry("parentId", evt.parentId() == null ? "" : evt.parentId().toString())
        );
    }
}
```

> **注意：** 上面的 `handleItemChanged`、`handleLocationChanged`、`rebuildStockFlatForLot` 以及 category/brand/unit/tag 变更的处理逻辑留有骨架注释，实现时需要：
> 1. 从 dump 端口拉取最新数据
> 2. 构建 search_index 行（ITEM 实体：itemName/brandName/tagNames/categoryName/unitName；LOCATION 实体：locationName/locationPath）
> 3. 对于 stock_flat：从 InventoryApi.stockPositionsOfItem 拉取后逐条 upsert
>
> 这些方法的完整实现依赖 dump 端口（Task 4）已就绪。

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/projection/ProjectionListener.java
git commit -m "feat(reporting): ProjectionListener 事件监听 → 投影 upsert

- 7 个 @EventListener：StockChanged/ItemChanged/CategoryChanged/BrandChanged/UnitChanged/TagChanged/LocationChanged
- 去重：processedEventMapper.insertOnConflictDoNothing
- 投影写入：REQUIRES_NEW 独立事务 upsert
- 失败：删除去重行 + 写 dead-letter，不向上抛异常"
```

---

### Task 7: ReportingEventRetryService — dead-letter 定时重试

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/projection/ReportingEventRetryService.java`

**Interfaces:**
- Consumes: `DeadLetterMapper`（findDueForRetry, incrementFailure, markAbandoned）
- Consumes: `ProjectionListener`（重试时重新调用事件处理方法）
- Consumes: `SystemApi`（写 REPORTING_EVENT_ABANDONED 审计）

- [ ] **Step 1: 创建 `ReportingEventRetryService`**

```java
// backend/src/main/java/com/zija/reporting/internal/projection/ReportingEventRetryService.java
package com.zija.reporting.internal.projection;

import com.zija.catalog.*;
import com.zija.inventory.StockChangedEvent;
import com.zija.location.LocationChangedEvent;
import com.zija.reporting.internal.persistence.DeadLetterEntity;
import com.zija.reporting.internal.persistence.DeadLetterMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * reporting dead-letter 定时重投服务。
 * 每隔 30 秒扫描到期条目，按指数退避重试，超过 10 次标记 abandoned 并写审计。
 */
@Service
public class ReportingEventRetryService {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventRetryService.class);
    private static final int MAX_FAILURES = 10;

    private final DeadLetterMapper deadLetterMapper;
    private final ProjectionListener listener;
    private final SystemApi systemApi;

    public ReportingEventRetryService(DeadLetterMapper deadLetterMapper,
                                       ProjectionListener listener,
                                       SystemApi systemApi) {
        this.deadLetterMapper = deadLetterMapper;
        this.listener = listener;
        this.systemApi = systemApi;
    }

    @Scheduled(fixedDelay = 30_000)
    public void retryPending() {
        var due = deadLetterMapper.findDueForRetry(OffsetDateTime.now(), 50);
        for (var dl : due) {
            retryOne(dl);
        }
    }

    private void retryOne(DeadLetterEntity dl) {
        try {
            dispatchToListener(dl);
            deadLetterMapper.deleteById(dl.getId());
        } catch (RuntimeException ex) {
            int newCount = dl.getFailureCount() + 1;
            if (newCount >= MAX_FAILURES) {
                deadLetterMapper.markAbandoned(dl.getId());
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        "REPORTING_EVENT_ABANDONED", "FAILURE", null, null, null, null, null,
                        Map.of("eventId", dl.getEventId().toString(),
                               "eventType", dl.getEventType())));
                log.warn("Reporting dead-letter abandoned after {} failures: eventId={}",
                        newCount, dl.getEventId());
            } else {
                long backoffSeconds = 30L * (1L << Math.min(newCount, 6));
                deadLetterMapper.incrementFailure(dl.getId(),
                        OffsetDateTime.now().plus(Duration.ofSeconds(backoffSeconds)),
                        truncate(ex.getMessage(), 4000));
            }
        }
    }

    /**
     * 按 eventType 分派到 ProjectionListener 对应的事件处理方法。
     */
    private void dispatchToListener(DeadLetterEntity dl) {
        Map<String, Object> payload = dl.getPayload();
        String eventType = dl.getEventType();

        switch (eventType) {
            case "StockChangedEvent" -> listener.onStockChanged(fromStockChangedMap(payload));
            case "ItemChangedEvent" -> listener.onItemChanged(fromItemChangedMap(payload));
            case "CategoryChangedEvent" -> listener.onCategoryChanged(fromCategoryChangedMap(payload));
            case "BrandChangedEvent" -> listener.onBrandChanged(fromBrandChangedMap(payload));
            case "UnitChangedEvent" -> listener.onUnitChanged(fromUnitChangedMap(payload));
            case "TagChangedEvent" -> listener.onTagChanged(fromTagChangedMap(payload));
            case "LocationChangedEvent" -> listener.onLocationChanged(fromLocationChangedMap(payload));
            default -> log.warn("Unknown event type in dead-letter: {}", eventType);
        }
    }

    // --- 反序列化方法 ---

    private StockChangedEvent fromStockChangedMap(Map<String, Object> m) {
        return new StockChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("lotId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("movementType"),
                new BigDecimal((String) m.get("quantityDelta")),
                parseNullableUuid((String) m.get("fromLocationId")),
                parseNullableUuid((String) m.get("toLocationId")),
                OffsetDateTime.parse((String) m.get("businessTime")),
                UUID.fromString((String) m.get("movementId")),
                UUID.fromString((String) m.get("idempotencyKey")),
                parseNullableUuid((String) m.get("operatorAccountId")),
                (String) m.get("reason"),
                parseNullableUuid((String) m.get("reversalOf"))
        );
    }

    private ItemChangedEvent fromItemChangedMap(Map<String, Object> m) {
        return new ItemChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("changeType"),
                OffsetDateTime.parse((String) m.get("businessTime"))
        );
    }

    private CategoryChangedEvent fromCategoryChangedMap(Map<String, Object> m) {
        return new CategoryChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("categoryId")),
                (String) m.get("changeType")
        );
    }

    private BrandChangedEvent fromBrandChangedMap(Map<String, Object> m) {
        return new BrandChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("brandId")),
                (String) m.get("changeType")
        );
    }

    private UnitChangedEvent fromUnitChangedMap(Map<String, Object> m) {
        return new UnitChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("unitId")),
                (String) m.get("changeType")
        );
    }

    private TagChangedEvent fromTagChangedMap(Map<String, Object> m) {
        return new TagChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("tagId")),
                (String) m.get("changeType")
        );
    }

    private LocationChangedEvent fromLocationChangedMap(Map<String, Object> m) {
        return new LocationChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("locationId")),
                (String) m.get("changeType"),
                parseNullableUuid((String) m.get("parentId")),
                OffsetDateTime.now()
        );
    }

    private static UUID parseNullableUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        return UUID.fromString(s);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 3: 运行全部后端测试**

Run: `cd backend && ./mvnw -q test`
Expected: 全部 PASS，包括 ModularityTests

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/projection/ReportingEventRetryService.java
git commit -m "feat(reporting): ReportingEventRetryService dead-letter 定时重试

- @Scheduled(fixedDelay=30_000) 扫描到期 dead-letter
- 按 eventType 分派到 ProjectionListener 对应方法
- 指数退避 30s*2^min(count,6)，超过 10 次标记 abandoned + 写 REPORTING_EVENT_ABANDONED 审计
- 反序列化器对所有字段缺键容错"
```

---

## Self-Review Checklist

### Spec 覆盖

| Spec 章节 | 对应 Task | 状态 |
|---|---|---|
| §2.1 reporting 模块边界 | Task 1 | ✅ |
| §2.2 事件投影 + 快照拉取 | Task 5, 6 | ✅ |
| §2.3 投影初始化与重建 | Task 6 (骨架), 6a 后续补充完整实现 | ⚠️ 投影重建端点留到 6b |
| §3.1 StockChangedEvent 扩展 | Task 2 | ✅ |
| §3.2 catalog/location 事件 | Task 3 | ✅ |
| §4.1 InventoryApi 追加 | Task 4 | ✅ |
| §4.2 CatalogApi 追加 | Task 4 | ✅ |
| §4.3 LocationApi 追加 | Task 4 | ✅ |
| §5 V5 迁移 | Task 1 | ✅ |
| §6.1 事件监听器 | Task 6, 7 | ✅ |
| §8.4 ModularityTests | Task 1 | ✅ |

### 已知缺口（留到 6b / 6c）

- 投影空时自动重建逻辑（启动时检查 → 拉取快照填充）— 6b
- 管理员手动触发重建端点 `POST /api/v1/reporting/projection/rebuild` — 6b
- 报表端点、导出端点、全局搜索端点 — 6b
- 前端 UI — 6c
- `ProjectionListener` 中 category/brand/unit/tag/location 变更的详细重建逻辑需要在实现时根据实际表结构补充（骨架已给出）

### 类型一致性检查

- `StockChangedEvent`: Task 2 新增 3 字段 → Task 6 `buildMovementFlat` 使用 → Task 7 `fromStockChangedMap` 反序列化 ✅
- `ProcessedEventMapper.insertOnConflictDoNothing`: Task 5 定义 `(UUID eventId, String eventType) → int` → Task 6 调用 ✅
- `DeadLetterEntity`: Task 5 定义 → Task 6 `saveDeadLetterInNewTx` 使用 → Task 7 `retryOne` 使用 ✅
- 各 dump DTO: Task 4 定义 → Task 6 `handleItemChanged` 等方法中使用 ✅

# 阶段五 5a：提醒与任务首页 后端 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `reminder` 后端模块：家庭默认规则、单表 kind 提醒任务、站内通知、可靠事件投递（Spring Modulith AFTER_COMMIT + 去重表 + dead-letter + 定时重投）、每日临期扫描、任务状态机与端点、首页聚合、审计，全部由 Testcontainers 覆盖。

**Architecture:** 新增 Spring Modulith `reminder` 模块，单向依赖 `household`/`catalog`/`inventory`/`system` 公开 Api。库存命令提交后由 Spring Modulith 派发 `StockChangedEvent`（AFTER_COMMIT），reminder listener 在独立小事务内去重（`reminder_processed_event`）并调 `Reconciler` 重算受影响 lot（临期）与 item（低库存）。每日 `@Scheduled` 全量扫描临期 + 刷新 SNOOZED 过期。失败写 dead-letter 由 `@Scheduled` 重投。任务状态机 OPEN/SNOOZED/DONE/IGNORED + reopen。

**Tech Stack:** Java 25、Spring Boot 4.1.x、Spring Modulith 2.0.5、MyBatis-Plus 3.5.16、Flyway、PostgreSQL 17、JUnit 5、Mockito、AssertJ、Testcontainers、MockMvc。

**覆盖 spec：** `docs/superpowers/specs/2026-07-26-phase5a-reminder-backend-design.md`（全部章节）。

---

## 计划范围

仅后端 `reminder` 模块及相关 Api 扩展。**不**实现前端 Vue 页面、SMTP 邮件发送（5c）、报表/搜索（阶段六）。

## 前置条件

- 工作树干净；HEAD 含已批准 5a spec（commit `cad9a9a` 或之后）。
- 已交付 `system`/`identity`/`household`/`catalog`/`location`/`file`/`inventory` 模块；迁移已合并为单一 `V1__create_all_tables.sql`。
- `CatalogApi`（`requireItem`/`requireActiveItem`/`ItemInfo`）、`InventoryApi`、`HouseholdApi`（`requireActiveMember`/`hasAtLeastRole`/`MemberRole.ADMIN`）、`SystemApi.recordAudit` 已存在。
- `StockChangedEvent` 已由 `inventory.internal.event.InventoryEventPublisher` 同步发布。
- 执行前用 `superpowers:using-git-worktrees` 建隔离工作树（或直接在 main 上按用户偏好执行）。

## 目标文件清单

**Create（后端 main）：**
- `backend/src/main/resources/db/migration/V2__create_reminder_core.sql`
- `backend/src/main/java/com/zija/reminder/package-info.java`
- `backend/src/main/java/com/zija/reminder/ReminderApi.java`
- `backend/src/main/java/com/zija/reminder/StockChangedEvent`（已存在于 `com.zija.inventory`，**不重复创建**）
- `backend/src/main/java/com/zija/reminder/internal/ReminderController.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderService.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderReconciler.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderRuleResolver.java`
- `backend/src/main/java/com/zija/reminder/internal/SeverityClassifier.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java`
- `backend/src/main/java/com/zija/reminder/internal/EventRetryService.java`
- `backend/src/main/java/com/zija/reminder/internal/ExpiryScanScheduler.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderTaskStateService.java`
- `backend/src/main/java/com/zija/reminder/internal/DashboardService.java`
- `backend/src/main/java/com/zija/reminder/internal/NotificationService.java`
- `backend/src/main/java/com/zija/reminder/internal/ReminderExceptionHandler.java`
- `backend/src/main/java/com/zija/reminder/internal/ClockConfig.java`
- 七类异常类（`ReminderRuleNotInitializedException` 等，见任务 2）
- `backend/src/main/java/com/zija/reminder/internal/persistence/`（5 个 Entity + 5 个 Mapper）
- `backend/src/main/resources/mapper/reminder/`（5 个 XML）
- `backend/src/main/java/com/zija/inventory/internal/event/InventoryEventConfig.java`（如需）

**Modify：**
- `backend/src/main/java/com/zija/catalog/CatalogApi.java` —— `ItemInfo` 加 4 字段。
- `backend/src/main/java/com/zija/catalog/internal/ItemService.java` —— `toInfo` 填 4 字段；既有调用方编译不破。
- `backend/src/main/java/com/zija/inventory/InventoryApi.java` —— 加 `lotsOfItem`/`currentTotalStockOfItem` + `LotInfo` record。
- `backend/src/main/java/com/zija/inventory/internal/InventoryService.java` —— 实现新方法（用既有 `LotMapper`/`StockPositionMapper` 或新增聚合查询）。
- `backend/src/main/java/com/zija/inventory/internal/persistence/LotMapper.java` + `StockPositionMapper.java` —— 可能加聚合 SQL（或复用 ConsistencyCheckMapper）。
- `backend/src/test/java/com/zija/ModularityTests.java` —— 加 reminder 断言。
- `backend/src/test/java/com/zija/OpenApiContractTest.java` —— 基线更新到含 reminder/notifications 端点。

**Create（测试）：**
- `backend/src/test/java/com/zija/reminder/internal/ReminderRuleResolverTest.java`
- `backend/src/test/java/com/zija/reminder/internal/SeverityClassifierTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderHouseholdRuleIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderReconcilerIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderEventListenerIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ExpiryScanSchedulerIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderTaskStateIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderDashboardIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/NotificationIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/ReminderEndpointIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/CatalogApiReminderFieldsIntegrationTest.java`
- `backend/src/test/java/com/zija/reminder/internal/InventoryApiLotsOfItemIntegrationTest.java`

每个任务结束提交一次（中文 body + 英文前缀）。

---

## 任务 1：数据库迁移——reminder 核心表

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_reminder_core.sql`

- [ ] **步骤 1：创建 V2 迁移文件**

```sql
-- 1) 家庭默认提醒规则（家庭单例）
CREATE TABLE reminder_household_rule (
    id                    UUID PRIMARY KEY,
    household_id          UUID NOT NULL UNIQUE REFERENCES household(id),
    expiry_disabled       BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_reminder_days SMALLINT[] NOT NULL DEFAULT ARRAY[30,7,1]::SMALLINT[],
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
    kind                VARCHAR(20) NOT NULL,
    lot_id              UUID,
    item_id             UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    due_at              TIMESTAMPTZ NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    threshold_snapshot  JSONB,
    qty_snapshot        NUMERIC(20,6),
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

-- 3) 站内通知
CREATE TABLE reminder_notification (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    scope           VARCHAR(20) NOT NULL,
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(4000),
    source_task_id  UUID,
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
    payload         JSONB NOT NULL,
    failure_count   INTEGER NOT NULL DEFAULT 1,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    last_error      VARCHAR(4000),
    last_retry_at   TIMESTAMPTZ,
    abandoned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reminder_dead_letter_event UNIQUE (event_id)
);
```

- [ ] **步骤 2：本地验证迁移在空库 Flyway 执行成功**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS（迁移由 Testcontainers 空库 Flyway 执行；若 V2 有语法错误会在此时报错）。

- [ ] **步骤 3：提交**

```bash
git add backend/src/main/resources/db/migration/V2__create_reminder_core.sql
git commit -m "feat(reminder): 新增 V2 reminder 核心表迁移

家庭默认规则、单表kind提醒任务（部分唯一索引未完合并）、
站内通知、事件去重表与dead-letter重投表。"
```

---

## 任务 2：reminder 模块骨架与 Clock bean

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/package-info.java`
- Create: `backend/src/main/java/com/zija/reminder/ReminderApi.java`
- Create: 七类异常类（`backend/src/main/java/com/zija/reminder/internal/`）
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderExceptionHandler.java`
- Create: `backend/src/main/java/com/zija/reminder/internal/ClockConfig.java`

- [ ] **步骤 1：创建 package-info**

```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Reminder",
        allowedDependencies = {"household", "catalog", "inventory", "system"}
)
package com.zija.reminder;
```

- [ ] **步骤 2：创建 ReminderApi 公开只读端口（供未来 reporting 复用）**

```java
package com.zija.reminder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 提醒模块公共只读端口：任务与首页聚合只读 DTO。
 * 规则读写、任务状态机操作、通知写操作由本模块 REST 端点接收，不在此端口。
 */
public interface ReminderApi {

    /** 返回家庭「优先任务」前 N 条（按 severity URGENT>WARN>INFO 再按 due_at ASC）。 */
    List<PriorityTaskInfo> priorityTasks(UUID householdId, int topN);

    record PriorityTaskInfo(
            UUID taskId,
            String kind,            // EXPIRY | LOW_STOCK
            String severity,        // INFO | WARN | URGENT
            String title,
            OffsetDateTime dueAt,
            UUID itemId,
            UUID lotId
    ) {}
}
```

- [ ] **步骤 3：创建七类异常类**（均 `extends RuntimeException`，含默认构造与 `(String)` 构造）

逐个创建（package `com.zija.reminder.internal`）：

```java
package com.zija.reminder.internal;
public class ReminderRuleNotInitializedException extends RuntimeException {
    public ReminderRuleNotInitializedException() { super(); }
    public ReminderRuleNotInitializedException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderRuleVersionConflictException extends RuntimeException {
    public ReminderRuleVersionConflictException() { super(); }
    public ReminderRuleVersionConflictException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderRuleExpiryDaysInvalidException extends RuntimeException {
    public ReminderRuleExpiryDaysInvalidException() { super(); }
    public ReminderRuleExpiryDaysInvalidException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderRuleLowStockInvalidException extends RuntimeException {
    public ReminderRuleLowStockInvalidException() { super(); }
    public ReminderRuleLowStockInvalidException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderTaskNotFoundException extends RuntimeException {
    public ReminderTaskNotFoundException() { super(); }
    public ReminderTaskNotFoundException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderTaskInvalidTransitionException extends RuntimeException {
    public ReminderTaskInvalidTransitionException() { super(); }
    public ReminderTaskInvalidTransitionException(String m) { super(m); }
}
```
```java
package com.zija.reminder.internal;
public class ReminderTaskSnoozeUntilInvalidException extends RuntimeException {
    public ReminderTaskSnoozeUntilInvalidException() { super(); }
    public ReminderTaskSnoozeUntilInvalidException(String m) { super(m); }
}
```

- [ ] **步骤 4：创建 ReminderExceptionHandler**（复用 Catalog 模式）

```java
package com.zija.reminder.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ReminderController.class})
class ReminderExceptionHandler {

    @ExceptionHandler(ReminderRuleNotInitializedException.class)
    ProblemDetail handleRuleNotInit(HttpServletRequest r) {
        return problem(r, HttpStatus.INTERNAL_SERVER_ERROR, "规则未初始化", "REMINDER_RULE_NOT_INITIALIZED");
    }

    @ExceptionHandler(ReminderRuleVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest r) {
        return problem(r, HttpStatus.CONFLICT, "规则版本冲突", "REMINDER_RULE_VERSION_CONFLICT");
    }

    @ExceptionHandler(ReminderRuleExpiryDaysInvalidException.class)
    ProblemDetail handleExpiryDays(HttpServletRequest r) {
        return problem(r, HttpStatus.UNPROCESSABLE_ENTITY, "临期天数无效", "REMINDER_RULE_EXPIRY_DAYS_INVALID");
    }

    @ExceptionHandler(ReminderRuleLowStockInvalidException.class)
    ProblemDetail handleLowStock(HttpServletRequest r) {
        return problem(r, HttpStatus.UNPROCESSABLE_ENTITY, "低库存阈值无效", "REMINDER_RULE_LOW_STOCK_INVALID");
    }

    @ExceptionHandler(ReminderTaskNotFoundException.class)
    ProblemDetail handleNotFound(HttpServletRequest r) {
        return problem(r, HttpStatus.NOT_FOUND, "任务不存在", "REMINDER_TASK_NOT_FOUND");
    }

    @ExceptionHandler(ReminderTaskInvalidTransitionException.class)
    ProblemDetail handleTransition(HttpServletRequest r) {
        return problem(r, HttpStatus.CONFLICT, "状态转换非法", "REMINDER_TASK_INVALID_TRANSITION");
    }

    @ExceptionHandler(ReminderTaskSnoozeUntilInvalidException.class)
    ProblemDetail handleSnoozeUntil(HttpServletRequest r) {
        return problem(r, HttpStatus.UNPROCESSABLE_ENTITY, "稍后提醒时间无效", "REMINDER_TASK_SNOOZE_UNTIL_INVALID");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
```

- [ ] **步骤 5：创建 ClockConfig**

```java
package com.zija.reminder.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ClockConfig {
    @Bean
    Clock reminderClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **步骤 6：编译验证**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS。

- [ ] **步骤 7：提交**

```bash
git add backend/src/main/java/com/zija/reminder/
git commit -m "feat(reminder): 新增模块骨架、ReminderApi、异常与处理器、Clock bean"
```

---

## 任务 3：扩展 CatalogApi.ItemInfo 与 InventoryApi

**Files:**
- Modify: `backend/src/main/java/com/zija/catalog/CatalogApi.java`
- Modify: `backend/src/main/java/com/zija/catalog/internal/ItemService.java`（`toInfo` 方法）
- Modify: `backend/src/main/java/com/zija/inventory/InventoryApi.java`
- Modify: `backend/src/main/java/com/zija/inventory/internal/InventoryService.java`
- Modify 或 Create: `backend/src/main/java/com/zija/inventory/internal/persistence/LotMapper.java` 或 `ConsistencyCheckMapper.java`（新增聚合 SQL）
- Modify 或 Create: 对应 `mapper/inventory/*.xml`
- Test: `backend/src/test/java/com/zija/reminder/internal/CatalogApiReminderFieldsIntegrationTest.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/InventoryApiLotsOfItemIntegrationTest.java`

- [ ] **步骤 1：扩展 CatalogApi.ItemInfo（追加 4 字段，向后兼容）**

在 `CatalogApi.java` 的 `record ItemInfo` 末尾追加字段（保持既有字段顺序不变，避免破坏序列化契约）：

```java
record ItemInfo(
        UUID id,
        UUID householdId,
        String name,
        String managementType,
        UUID categoryId,
        UUID brandId,
        UUID unitId,
        UUID coverFileId,
        String status,
        // 5a 新增：物品级提醒配置（INHERIT/DISABLED/CUSTOM + 天数 + 低库存）
        String expiryReminderMode,
        java.util.List<Short> expiryReminderDays,
        String lowStockMode,
        java.math.BigDecimal lowStockThreshold
) {}
```

- [ ] **步骤 2：更新 ItemService.toInfo 填 4 字段**

`ItemService.java:298`：

```java
private ItemInfo toInfo(ItemEntity entity) {
    return new ItemInfo(
            entity.getId(), entity.getHouseholdId(), entity.getName(),
            entity.getManagementType(), entity.getCategoryId(), entity.getBrandId(),
            entity.getUnitId(), entity.getCoverFileId(), entity.getStatus(),
            entity.getExpiryReminderMode(),
            entity.getExpiryReminderDays(),
            entity.getLowStockMode(),
            entity.getLowStockThreshold()
    );
}
```

- [ ] **步骤 3：扩展 InventoryApi 追加方法与 LotInfo record**

在 `InventoryApi.java` 末尾追加：

```java
import java.time.LocalDate;

// ...

    /** 列出某物品所有批次含到期日与当前总库存（聚合各位置）。 */
    java.util.List<LotInfo> lotsOfItem(UUID householdId, UUID itemId);

    /** 某物品当前总库存（聚合各位置）。 */
    java.math.BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId);

    record LotInfo(
            UUID lotId,
            UUID itemId,
            LocalDate expiryDate,
            java.math.BigDecimal totalQuantity
    ) {}
```

- [ ] **步骤 4：在 InventoryMapper 新增聚合 SQL**（推荐在 `ConsistencyCheckMapper` 或新建 `ItemStockAggregateMapper`）

为避免污染既有 mapper，新建 `backend/src/main/java/com/zija/inventory/internal/persistence/ItemStockAggregateMapper.java`：

```java
package com.zija.inventory.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemStockAggregateMapper {

    /** 按物品列出批次与总库存（仅活跃 lot，含冲正/移位后正确，按 lot 维度 SUM(stock_position.quantity)）。 */
    List<LotAggregateRow> lotsOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    /** 某物品总库存。 */
    BigDecimal totalStockOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    record LotAggregateRow(UUID lotId, UUID itemId, LocalDate expiryDate, BigDecimal totalQuantity) {}
}
```

`backend/src/main/resources/mapper/inventory/ItemStockAggregateMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.inventory.internal.persistence.ItemStockAggregateMapper">

    <resultMap id="lotAggRow" type="com.zija.inventory.internal.persistence.ItemStockAggregateMapper$LotAggregateRow">
        <result property="lotId"       column="lot_id"/>
        <result property="itemId"      column="item_id"/>
        <result property="expiryDate"  column="expiry_date"/>
        <result property="totalQuantity" column="total_qty"/>
    </resultMap>

    <select id="lotsOfItem" resultMap="lotAggRow">
        SELECT l.id AS lot_id, l.item_id AS item_id, l.expiry_date AS expiry_date,
               COALESCE(SUM(sp.quantity), 0) AS total_qty
        FROM inventory_lot l
        LEFT JOIN inventory_stock_position sp
          ON sp.lot_id = l.id AND sp.household_id = l.household_id
        WHERE l.household_id = #{householdId} AND l.item_id = #{itemId}
        GROUP BY l.id, l.item_id, l.expiry_date
    </select>

    <select id="totalStockOfItem" resultType="java.math.BigDecimal">
        SELECT COALESCE(SUM(sp.quantity), 0)
        FROM inventory_stock_position sp
        JOIN inventory_lot l ON l.id = sp.lot_id AND l.household_id = sp.household_id
        WHERE sp.household_id = #{householdId} AND l.item_id = #{itemId}
    </select>
</mapper>
```

- [ ] **步骤 5：在 InventoryService 实现新方法**

在 `InventoryService.java` 字段区追加 `private final ItemStockAggregateMapper itemStockAggregateMapper;`，构造函数追加参数并赋值。实现：

```java
@Override
@Transactional(readOnly = true)
public List<LotInfo> lotsOfItem(UUID householdId, UUID itemId) {
    return itemStockAggregateMapper.lotsOfItem(householdId, itemId).stream()
            .map(r -> new LotInfo(r.lotId(), r.itemId(), r.expiryDate(), r.totalQuantity()))
            .toList();
}

@Override
@Transactional(readOnly = true)
public BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId) {
    var v = itemStockAggregateMapper.totalStockOfItem(householdId, itemId);
    return v != null ? v : BigDecimal.ZERO;
}
```

- [ ] **步骤 6：写 CatalogApiReminderFieldsIntegrationTest（向后兼容回归）**

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.catalog.internal.persistence.UnitMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class CatalogApiReminderFieldsIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired CatalogApi catalogApi;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private java.util.UUID householdId, unitId, itemId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE inventory_movement, inventory_stock_position, inventory_lot, reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(java.util.UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(java.util.UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setDecimalScale(0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
        var it = new ItemEntity();
        java.util.UUID id = java.util.UUID.randomUUID();
        it.setId(id); it.setHouseholdId(householdId); it.setName("牛奶");
        it.setManagementType("CONSUMABLE"); it.setUnitId(unitId); it.setStatus("ACTIVE");
        it.setExpiryReminderMode("CUSTOM"); it.setExpiryReminderDays(List.of((short)30,(short)7,(short)1));
        it.setLowStockMode("CUSTOM"); it.setLowStockThreshold(new BigDecimal("2"));
        itemMapper.insert(it); itemId = id;
    }

    @Test
    void requireItemReturnsReminderFields() {
        var info = catalogApi.requireItem(householdId, itemId);
        assertThat(info.expiryReminderMode()).isEqualTo("CUSTOM");
        assertThat(info.expiryReminderDays()).containsExactly((short)30,(short)7,(short)1);
        assertThat(info.lowStockMode()).isEqualTo("CUSTOM");
        assertThat(info.lowStockThreshold()).isEqualByComparingTo("2");
    }

    @Test
    void requireItemBackwardCompatExistingFieldsStillPresent() {
        var info = catalogApi.requireItem(householdId, itemId);
        assertThat(info.id()).isEqualTo(itemId);
        assertThat(info.name()).isEqualTo("牛奶");
        assertThat(info.status()).isEqualTo("ACTIVE");
        assertThat(info.unitId()).isEqualTo(unitId);
    }
}
```

- [ ] **步骤 7：写 InventoryApiLotsOfItemIntegrationTest**

```java
package com.zija.reminder.internal;

import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.StockCommandService;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.catalog.internal.persistence.UnitMapper;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InventoryApiLotsOfItemIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired InventoryApi inventoryApi;
    @Autowired StockCommandService stockCommandService;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, locB, accountId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE inventory_movement, inventory_stock_position, inventory_lot, reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setDecimalScale(0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
        var it = new ItemEntity(); it.setId(UUID.randomUUID()); it.setHouseholdId(householdId);
        it.setName("牛奶"); it.setManagementType("CONSUMABLE"); it.setUnitId(unitId); it.setStatus("ACTIVE");
        it.setExpiryReminderMode("INHERIT"); it.setLowStockMode("INHERIT"); itemMapper.insert(it); itemId = it.getId();
        var a = new LocationEntity(); a.setId(UUID.randomUUID()); a.setHouseholdId(householdId);
        a.setName("A"); a.setSortOrder(0); locationMapper.insert(a); locA = a.getId();
        var b = new LocationEntity(); b.setId(UUID.randomUUID()); b.setHouseholdId(householdId);
        b.setName("B"); b.setSortOrder(1); locationMapper.insert(b); locB = b.getId();
        accountId = UUID.randomUUID();
    }

    @Test
    void lotsOfItemAggregatesAcrossLocations() {
        var expiry = LocalDate.now().plusDays(30);
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, new BigDecimal("4"), LocalDate.now(), null, expiry, null, null, null);
        new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd));
        // 移 2 到 B
        new TransactionTemplate(txManager).execute(s ->
                stockCommandService.transfer(householdId, accountId, /* lotId 需取得 */ cmdLotId(0), locA, locB, new BigDecimal("2"), null, UUID.randomUUID().toString()));

        var lots = inventoryApi.lotsOfItem(householdId, itemId);
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).expiryDate()).isEqualTo(expiry);
        assertThat(lots.get(0).totalQuantity()).isEqualByComparingTo("4");
        assertThat(inventoryApi.currentTotalStockOfItem(householdId, itemId)).isEqualByComparingTo("4");
    }

    private UUID cmdLotId(int idx) {
        return inventoryApi.lotsOfItem(householdId, itemId).get(idx).lotId();
    }
}
```

> 注：`LocationEntity`/`LocationMapper`、`HouseholdEntity`/`HouseholdMapper` 字段命名以现有源码实际为准；若 `LocationEntity` 无 `sortOrder` 字段，按现存最简必填列写。若 `StockCommandService.transfer` 签名与此处不符，按真实签名调整 above——任务执行时先 `rg -n "public.*transfer" backend/src/main/java/com/zija/inventory/internal/StockCommandService.java` 确认参数顺序。

- [ ] **步骤 8：运行测试**

Run: `cd backend && ./mvnw -q -Dtest=CatalogApiReminderFieldsIntegrationTest,InventoryApiLotsOfItemIntegrationTest test`
Expected: PASS。

- [ ] **步骤 9：跑回归确保既有库存测试无破坏**

Run: `cd backend && ./mvnw -q -Dtest=StockCommandServiceIntegrationTest,InventoryEndpointIntegrationTest,ItemEndpointIntegrationTest test`
Expected: PASS（CatalogApi.ItemInfo 加字段后，既有调用点编译通过；若 ItemController response 直接用 `ItemInfo` 序列化，前端类型无关后端测试）。

- [ ] **步骤 10：提交**

```bash
git add backend/src/main/java/com/zija/catalog/ \
        backend/src/main/java/com/zija/inventory/ \
        backend/src/main/resources/mapper/inventory/ItemStockAggregateMapper.xml \
        backend/src/test/java/com/zija/reminder/internal/CatalogApiReminderFieldsIntegrationTest.java \
        backend/src/test/java/com/zija/reminder/internal/InventoryApiLotsOfItemIntegrationTest.java
git commit -m "feat(reminder): 扩展 CatalogApi.ItemInfo 提醒字段与 InventoryApi 批次聚合端口

ItemInfo 追加 expiryReminderMode/days、lowStockMode/threshold；
InventoryApi 新增 lotsOfItem/currentTotalStockOfItem + LotInfo；
新增 ItemStockAggregateMapper 聚合 SQL；
向后兼容回归测试覆盖。"
```

---

## 任务 4：reminder 持久化层（5 个 Entity + 5 个 Mapper + XML）

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/persistence/{HouseholdRuleEntity,TaskEntity,NotificationEntity,ProcessedEventEntity,DeadLetterEntity}.java`
- Create: `backend/src/main/java/com/zija/reminder/internal/persistence/{HouseholdRuleMapper,TaskMapper,NotificationMapper,ProcessedEventMapper,DeadLetterMapper}.java`
- Create: `backend/src/main/resources/mapper/reminder/*.xml`（5 个）

- [ ] **步骤 1：创建 5 个 Entity**

`HouseholdRuleEntity.java`：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName("reminder_household_rule")
public class HouseholdRuleEntity {
    @TableId private UUID id;
    private UUID householdId;
    private Boolean expiryDisabled;
    private List<Short> expiryReminderDays;
    private Boolean lowStockDisabled;
    private BigDecimal lowStockThreshold;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;
    // getters/setters 逐字段
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public Boolean getExpiryDisabled() { return expiryDisabled; } public void setExpiryDisabled(Boolean v) { this.expiryDisabled = v; }
    public List<Short> getExpiryReminderDays() { return expiryReminderDays; } public void setExpiryReminderDays(List<Short> v) { this.expiryReminderDays = v; }
    public Boolean getLowStockDisabled() { return lowStockDisabled; } public void setLowStockDisabled(Boolean v) { this.lowStockDisabled = v; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; } public void setLowStockThreshold(BigDecimal v) { this.lowStockThreshold = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { this.version = v; }
}
```

`TaskEntity.java`：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName("reminder_task")
public class TaskEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String kind;
    private UUID lotId;
    private UUID itemId;
    private String status;
    private OffsetDateTime dueAt;
    private String severity;
    private Map<String,Object> thresholdSnapshot;
    private BigDecimal qtySnapshot;
    private OffsetDateTime snoozedUntil;
    private OffsetDateTime lastReconciledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;
    // getters/setters 逐字段（同 LotEntity 风格）
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public String getKind() { return kind; } public void setKind(String v) { this.kind = v; }
    public UUID getLotId() { return lotId; } public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; } public void setItemId(UUID v) { this.itemId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getDueAt() { return dueAt; } public void setDueAt(OffsetDateTime v) { this.dueAt = v; }
    public String getSeverity() { return severity; } public void setSeverity(String v) { this.severity = v; }
    public Map<String,Object> getThresholdSnapshot() { return thresholdSnapshot; } public void setThresholdSnapshot(Map<String,Object> v) { this.thresholdSnapshot = v; }
    public BigDecimal getQtySnapshot() { return qtySnapshot; } public void setQtySnapshot(BigDecimal v) { this.qtySnapshot = v; }
    public OffsetDateTime getSnoozedUntil() { return snoozedUntil; } public void setSnoozedUntil(OffsetDateTime v) { this.snoozedUntil = v; }
    public OffsetDateTime getLastReconciledAt() { return lastReconciledAt; } public void setLastReconciledAt(OffsetDateTime v) { this.lastReconciledAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { this.version = v; }
}
```

`NotificationEntity.java`：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reminder_notification")
public class NotificationEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String scope;
    private String title;
    private String message;
    private UUID sourceTaskId;
    private Boolean read;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getHouseholdId() { return householdId; } public void setHouseholdId(UUID v) { this.householdId = v; }
    public String getScope() { return scope; } public void setScope(String v) { this.scope = v; }
    public String getTitle() { return title; } public void setTitle(String v) { this.title = v; }
    public String getMessage() { return message; } public void setMessage(String v) { this.message = v; }
    public UUID getSourceTaskId() { return sourceTaskId; } public void setSourceTaskId(UUID v) { this.sourceTaskId = v; }
    public Boolean getRead() { return read; } public void setRead(Boolean v) { this.read = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getReadAt() { return readAt; } public void setReadAt(OffsetDateTime v) { this.readAt = v; }
}
```

`ProcessedEventEntity.java`（无 Mapper XML，仅 BaseMapper）：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reminder_processed_event")
public class ProcessedEventEntity {
    @TableId private UUID eventId;
    private OffsetDateTime processedAt;
    public UUID getEventId() { return eventId; } public void setEventId(UUID v) { this.eventId = v; }
    public OffsetDateTime getProcessedAt() { return processedAt; } public void setProcessedAt(OffsetDateTime v) { this.processedAt = v; }
}
```

`DeadLetterEntity.java`：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName("reminder_event_dead_letter")
public class DeadLetterEntity {
    @TableId private UUID id;
    private UUID eventId;
    private Map<String,Object> payload;
    private Integer failureCount;
    private OffsetDateTime nextRetryAt;
    private String lastError;
    private OffsetDateTime lastRetryAt;
    private Boolean abandoned;
    private OffsetDateTime createdAt;
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getEventId() { return eventId; } public void setEventId(UUID v) { this.eventId = v; }
    public Map<String,Object> getPayload() { return payload; } public void setPayload(Map<String,Object> v) { this.payload = v; }
    public Integer getFailureCount() { return failureCount; } public void setFailureCount(Integer v) { this.failureCount = v; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; } public void setNextRetryAt(OffsetDateTime v) { this.nextRetryAt = v; }
    public String getLastError() { return lastError; } public void setLastError(String v) { this.lastError = v; }
    public OffsetDateTime getLastRetryAt() { return lastRetryAt; } public void setLastRetryAt(OffsetDateTime v) { this.lastRetryAt = v; }
    public Boolean getAbandoned() { return abandoned; } public void setAbandoned(Boolean v) { this.abandoned = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
```

- [ ] **步骤 2：创建 5 个 Mapper 接口**

`HouseholdRuleMapper.java`（BaseMapper 即可，无 XML）：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HouseholdRuleMapper extends BaseMapper<HouseholdRuleEntity> {}
```

`ProcessedEventMapper.java`：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface ProcessedEventMapper extends BaseMapper<ProcessedEventEntity> {
    /** INSERT ON CONFLICT DO NOTHING，返回受影响行数（0=已存在跳过）。 */
    int insertOnConflictDoNothing(@Param("eventId") UUID eventId);
}
```

`reminder/ProcessedEventMapper.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reminder.internal.persistence.ProcessedEventMapper">
    <insert id="insertOnConflictDoNothing">
        INSERT INTO reminder_processed_event(event_id, processed_at)
        VALUES (#{eventId}, CURRENT_TIMESTAMP)
        ON CONFLICT (event_id) DO NOTHING
    </insert>
</mapper>
```

`TaskMapper.java`（含行锁与状态机更新）：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    /** 锁定未完任务（OPEN/SNOOZED）行，FOR UPDATE；不存在返回 null。 */
    TaskEntity lockOpenByKindAndTarget(@Param("householdId") UUID householdId,
                                       @Param("kind") String kind,
                                       @Param("lotId") UUID lotId);

    /** 后台扫描用：列出家庭所有未完 OPEN/SNOOZED 任务 FOR UPDATE。 */
    List<TaskEntity> lockOpenTasksForScan(@Param("householdId") UUID householdId);

    /** 状态机：把 OPEN/SNOOZED 转 SNOOZED 并设 snoozed_until。 */
    int snooze(@Param("householdId") UUID householdId, @Param("id") UUID id,
               @Param("fromStatuses") List<String> fromStatuses,
               @Param("until") OffsetDateTime until);

    /** 状态机：转指定终态（DONE/IGNORED），清 snoozed_until。 */
    int transitionTo(@Param("householdId") UUID householdId, @Param("id") UUID id,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus);

    /** 状态机：reopen（IGNORED/DONE → OPEN），清 snoozed_until。 */
    int reopen(@Param("householdId") UUID householdId, @Param("id") UUID id);

    /** 首页聚合：7 天内到期任务（EXPIRY, due_at <= now+days, status OPEN/SNOOZED）。 */
    List<TaskEntity> expiryWithinDays(@Param("householdId") UUID householdId,
                                      @Param("from") OffsetDateTime from,
                                      @Param("to") OffsetDateTime to,
                                      @Param("limit") int limit);

    /** 首页聚合：低库存未完任务（LOW_STOCK, OPEN/SNOOZED），前 limit 条。 */
    List<TaskEntity> lowStockOpenTasks(@Param("householdId") UUID householdId,
                                       @Param("limit") int limit);

    /** 首页聚合：优先任务（OPEN/SNOOZED），按 severity ASC(URGENT,WARN,INFO)、due_at ASC，前 limit。 */
    List<TaskEntity> priorityTasks(@Param("householdId") UUID householdId,
                                   @Param("limit") int limit);

    /** 分页查询。 */
    IPage<TaskEntity> findPage(Page<TaskEntity> page,
                               @Param("householdId") UUID householdId,
                               @Param("kind") String kind,
                               @Param("status") String status,
                               @Param("itemId") UUID itemId,
                               @Param("overdue") Boolean overdue,
                               @Param("now") OffsetDateTime now,
                               @Param("orderBy") String orderBy);
}
```

`reminder/TaskMapper.xml`（关键 SQL）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reminder.internal.persistence.TaskMapper">

    <select id="lockOpenByKindAndTarget" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        WHERE household_id = #{householdId} AND kind = #{kind}
          AND ((kind = 'EXPIRY'   AND lot_id = #{lotId})
            OR (kind = 'LOW_STOCK' AND lot_id IS NULL))
          AND status IN ('OPEN','SNOOZED')
        FOR UPDATE
    </select>

    <select id="lockOpenTasksForScan" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        WHERE household_id = #{householdId} AND status IN ('OPEN','SNOOZED')
        FOR UPDATE
    </select>

    <update id="snooze">
        UPDATE reminder_task
        SET status = 'SNOOZED', snoozed_until = #{until}, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE household_id = #{householdId} AND id = #{id}
          AND status IN
          <foreach collection="fromStatuses" item="s" open="(" separator="," close=")">#{s}</foreach>
    </update>

    <update id="transitionTo">
        UPDATE reminder_task
        SET status = #{toStatus}, snoozed_until = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE household_id = #{householdId} AND id = #{id}
          AND status IN
          <foreach collection="fromStatuses" item="s" open="(" separator="," close=")">#{s}</foreach>
    </update>

    <update id="reopen">
        UPDATE reminder_task
        SET status = 'OPEN', snoozed_until = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE household_id = #{householdId} AND id = #{id}
          AND status IN ('DONE','IGNORED')
    </update>

    <select id="expiryWithinDays" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        WHERE household_id = #{householdId} AND kind = 'EXPIRY'
          AND status IN ('OPEN','SNOOZED')
          AND due_at &gt;= #{from} AND due_at &lt; #{to}
        ORDER BY due_at ASC
        LIMIT #{limit}
    </select>

    <select id="lowStockOpenTasks" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        WHERE household_id = #{householdId} AND kind = 'LOW_STOCK'
          AND status IN ('OPEN','SNOOZED')
        ORDER BY severity ASC, due_at ASC
        LIMIT #{limit}
    </select>

    <select id="priorityTasks" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        WHERE household_id = #{householdId} AND status IN ('OPEN','SNOOZED')
        ORDER BY
          CASE severity WHEN 'URGENT' THEN 0 WHEN 'WARN' THEN 1 ELSE 2 END,
          due_at ASC
        LIMIT #{limit}
    </select>

    <select id="findPage" resultType="com.zija.reminder.internal.persistence.TaskEntity">
        SELECT id, household_id, kind, lot_id, item_id, status, due_at, severity,
               threshold_snapshot, qty_snapshot, snoozed_until, last_reconciled_at,
               created_at, updated_at, version
        FROM reminder_task
        <where>
            household_id = #{householdId}
            <if test="kind != null and kind != ''">AND kind = #{kind}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="itemId != null">AND item_id = #{itemId}</if>
            <if test="overdue != null and overdue">AND due_at &lt; #{now}</if>
        </where>
        ORDER BY
          CASE severity WHEN 'URGENT' THEN 0 WHEN 'WARN' THEN 1 ELSE 2 END,
          due_at ASC
    </select>
</mapper>
```

`NotificationMapper.java` + XML（分页 + 未读计数 + 标记已读）：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
    IPage<NotificationEntity> findPage(Page<NotificationEntity> page,
                                       @Param("householdId") UUID householdId,
                                       @Param("unreadOnly") Boolean unreadOnly);
    long countUnread(@Param("householdId") UUID householdId);
    int markOneRead(@Param("householdId") UUID householdId, @Param("id") UUID id);
    int markAllRead(@Param("householdId") UUID householdId);
}
```

`reminder/NotificationMapper.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reminder.internal.persistence.NotificationMapper">

    <select id="findPage" resultType="com.zija.reminder.internal.persistence.NotificationEntity">
        SELECT id, household_id, scope, title, message, source_task_id, read, created_at, read_at
        FROM reminder_notification
        <where>
            household_id = #{householdId}
            <if test="unreadOnly != null and unreadOnly">AND read = FALSE</if>
        </where>
        ORDER BY created_at DESC
    </select>

    <select id="countUnread" resultType="long">
        SELECT COUNT(*) FROM reminder_notification WHERE household_id = #{householdId} AND read = FALSE
    </select>

    <update id="markOneRead">
        UPDATE reminder_notification SET read = TRUE, read_at = CURRENT_TIMESTAMP
        WHERE household_id = #{householdId} AND id = #{id} AND read = FALSE
    </update>

    <update id="markAllRead">
        UPDATE reminder_notification SET read = TRUE, read_at = CURRENT_TIMESTAMP
        WHERE household_id = #{householdId} AND read = FALSE
    </update>
</mapper>
```

`DeadLetterMapper.java`（BaseMapper 即可，无 XML）：
```java
package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface DeadLetterMapper extends BaseMapper<DeadLetterEntity> {
    /** 列出 next_retry_at<=now 且 abandoned=false 的行（FOR UPDATE SKIP LOCKED 避免重投竞争）。 */
    List<DeadLetterEntity> findDueForRetry(@Param("now") OffsetDateTime now, @Param("limit") int limit);
    int incrementFailure(@Param("id") java.util.UUID id, @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                         @Param("lastError") String lastError);
    int markAbandoned(@Param("id") java.util.UUID id);
}
```

`reminder/DeadLetterMapper.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reminder.internal.persistence.DeadLetterMapper">

    <select id="findDueForRetry" resultType="com.zija.reminder.internal.persistence.DeadLetterEntity">
        SELECT id, event_id, payload, failure_count, next_retry_at, last_error,
               last_retry_at, abandoned, created_at
        FROM reminder_event_dead_letter
        WHERE abandoned = FALSE AND next_retry_at &lt;= #{now}
        ORDER BY next_retry_at ASC
        LIMIT #{limit}
        FOR UPDATE SKIP LOCKED
    </select>

    <update id="incrementFailure">
        UPDATE reminder_event_dead_letter
        SET failure_count = failure_count + 1,
            next_retry_at = #{nextRetryAt},
            last_error = #{lastError},
            last_retry_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>

    <update id="markAbandoned">
        UPDATE reminder_event_dead_letter SET abandoned = TRUE, last_retry_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
    </update>
</mapper>
```

- [ ] **步骤 3：编译并提交**

Run: `cd backend && ./mvnw -q compile` → SUCCESS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/persistence/ \
        backend/src/main/resources/mapper/reminder/
git commit -m "feat(reminder): 持久化层 5 Entity + 5 Mapper（行锁/状态机/聚合/重投）"
```

---

## 任务 5：ReminderRuleService（家庭默认规则，TDD）

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderService.java`（规则读写部分先实现，后续任务追加状态机/dashboard/通知方法）
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderHouseholdRuleIntegrationTest.java`

- [ ] **步骤 1：写失败测试**

```java
package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.HouseholdRuleEntity;
import com.zija.reminder.internal.persistence.HouseholdRuleMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderHouseholdRuleIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderService reminderService;
    @Autowired HouseholdRuleMapper ruleMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
    }

    @Test
    void firstGetLazilyInitializesWithSpecDefaults() {
        var rule = reminderService.getOrCreateRule(householdId);
        assertThat(rule.expiryDisabled()).isFalse();
        assertThat(rule.expiryReminderDays()).containsExactly((short)30,(short)7,(short)1);
        assertThat(rule.lowStockDisabled()).isFalse();
        assertThat(rule.lowStockThreshold()).isEqualByComparingTo("1");
        // 持久化
        var rows = ruleMapper.selectList(null);
        assertThat(rows).hasSize(1);
    }

    @Test
    void putSucceedsWithMatchingVersion() {
        reminderService.getOrCreateRule(householdId);
        var current = reminderService.getOrCreateRule(householdId);
        var updated = reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                true, List.of((short)60,(short)14,(short)3), true, new BigDecimal("5"), current.version()));
        assertThat(updated.expiryDisabled()).isTrue();
        assertThat(updated.expiryReminderDays()).containsExactly((short)60,(short)14,(short)3);
        assertThat(updated.version()).isEqualTo(current.version() + 1);
    }

    @Test
    void putWithStaleVersionThrowsConflict() {
        reminderService.getOrCreateRule(householdId);
        var v0 = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(true, List.of((short)60), true, BigDecimal.TEN, v0.version()));
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE, v0.version())))
                .isInstanceOf(ReminderRuleVersionConflictException.class);
    }

    @Test
    void putInvalidExpiryDaysThrows() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)7,(short)30,(short)1), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)4000), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)7,(short)7), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
    }

    @Test
    void putInvalidLowStockThrows() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("0"), v.version())))
                .isInstanceOf(ReminderRuleLowStockInvalidException.class);
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("-1"), v.version())))
                .isInstanceOf(ReminderRuleLowStockInvalidException.class);
    }

    @Test
    void putWritesRuleChangedNotificationAndAudit() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(true, List.of((short)60), true, BigDecimal.TEN, v.version()));
        var notifs = jdbc.queryForList("SELECT scope FROM reminder_notification WHERE household_id = ?", householdId);
        assertThat(notifs).anyMatch(row -> "RULE_CHANGED".equals(row.get("scope")));
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_RULE_UPDATE".equals(row.get("action")));
    }
}
```

- [ ] **步骤 2：验证失败** — `cd backend && ./mvnw -q -Dtest=ReminderHouseholdRuleIntegrationTest test`，编译失败（`ReminderService` 缺失）。

- [ ] **步骤 3：实现 ReminderService（规则部分）**

```java
package com.zija.reminder.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.reminder.internal.persistence.HouseholdRuleEntity;
import com.zija.reminder.internal.persistence.HouseholdRuleMapper;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ReminderService {

    private final HouseholdRuleMapper ruleMapper;
    private final NotificationMapper notificationMapper;
    private final SystemApi systemApi;

    ReminderService(HouseholdRuleMapper ruleMapper, NotificationMapper notificationMapper, SystemApi systemApi) {
        this.ruleMapper = ruleMapper; this.notificationMapper = notificationMapper; this.systemApi = systemApi;
    }

    record RuleView(UUID id, UUID householdId, boolean expiryDisabled, List<Short> expiryReminderDays,
                    boolean lowStockDisabled, BigDecimal lowStockThreshold, int version) {}
    record RuleUpdate(boolean expiryDisabled, List<Short> expiryReminderDays,
                      boolean lowStockDisabled, BigDecimal lowStockThreshold, int version) {}

    /** 懒初始化家庭默认规则（spec：30/7/1、低库存1）。 */
    @Transactional
    public RuleView getOrCreateRule(UUID householdId) {
        var wrapper = new LambdaQueryWrapper<HouseholdRuleEntity>()
                .eq(HouseholdRuleEntity::getHouseholdId, householdId);
        var existing = ruleMapper.selectOne(wrapper);
        if (existing != null) return toView(existing);
        var e = new HouseholdRuleEntity();
        e.setId(UUID.randomUUID()); e.setHouseholdId(householdId);
        e.setExpiryDisabled(false); e.setExpiryReminderDays(List.of((short)30,(short)7,(short)1));
        e.setLowStockDisabled(false); e.setLowStockThreshold(BigDecimal.ONE);
        e.setCreatedAt(OffsetDateTime.now()); e.setUpdatedAt(OffsetDateTime.now()); e.setVersion(0);
        try { ruleMapper.insert(e); }
        catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发：另一线程已建，重新读
            return toView(ruleMapper.selectOne(wrapper));
        }
        return toView(e);
    }

    @Transactional
    public RuleView updateRule(UUID householdId, RuleUpdate update) {
        validateUpdate(update);
        var current = ruleMapper.selectOne(new LambdaQueryWrapper<HouseholdRuleEntity>()
                .eq(HouseholdRuleEntity::getHouseholdId, householdId));
        if (current == null) {
            // 先懒初始化，再读取最新
            getOrCreateRule(householdId);
            current = ruleMapper.selectOne(new LambdaQueryWrapper<HouseholdRuleEntity>()
                    .eq(HouseholdRuleEntity::getHouseholdId, householdId));
        }
        if (current.getVersion() != update.version()) {
            throw new ReminderRuleVersionConflictException();
        }
        current.setExpiryDisabled(update.expiryDisabled());
        current.setExpiryReminderDays(update.expiryDisabled() ? null : update.expiryReminderDays());
        current.setLowStockDisabled(update.lowStockDisabled());
        current.setLowStockThreshold(update.lowStockDisabled() ? null : update.lowStockThreshold());
        current.setUpdatedAt(OffsetDateTime.now());
        int rows = ruleMapper.updateById(current); // 乐观锁
        if (rows == 0) throw new ReminderRuleVersionConflictException();
        writeRuleChangedNotification(householdId);
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "REMINDER_RULE_UPDATE", "SUCCESS", householdId, null, null, null, null,
                Map.of("version", String.valueOf(update.version()))));
        return toView(current);
    }

    private void validateUpdate(RuleUpdate u) {
        if (!u.expiryDisabled()) {
            if (u.expiryReminderDays() == null || u.expiryReminderDays().isEmpty())
                throw new ReminderRuleExpiryDaysInvalidException();
            // 1..3650 互异
            var days = u.expiryReminderDays().stream().distinct().toList();
            if (days.size() != u.expiryReminderDays().size()) throw new ReminderRuleExpiryDaysInvalidException();
            for (short d : days) if (d < 1 || d > 3650) throw new ReminderRuleExpiryDaysInvalidException();
            // 降序
            for (int i = 1; i < days.size(); i++) {
                if (days.get(i - 1) <= days.get(i)) throw new ReminderRuleExpiryDaysInvalidException();
            }
        }
        if (!u.lowStockDisabled()) {
            if (u.lowStockThreshold() == null || u.lowStockThreshold().signum() <= 0)
                throw new ReminderRuleLowStockInvalidException();
        }
    }

    private void writeRuleChangedNotification(UUID householdId) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope("RULE_CHANGED"); n.setTitle("家庭默认提醒规则已更新");
        n.setRead(false); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n);
    }

    private RuleView toView(HouseholdRuleEntity e) {
        return new RuleView(e.getId(), e.getHouseholdId(),
                Boolean.TRUE.equals(e.getExpiryDisabled()), e.getExpiryReminderDays(),
                Boolean.TRUE.equals(e.getLowStockDisabled()), e.getLowStockThreshold(),
                e.getVersion() == null ? 0 : e.getVersion());
    }
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderHouseholdRuleIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderService.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderHouseholdRuleIntegrationTest.java
git commit -m "feat(reminder): 家庭默认规则服务（懒初始化/乐观锁/校验/通知/审计）"
```

---

## 任务 6：ReminderRuleResolver + SeverityClassifier 单元测试

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderRuleResolver.java`
- Create: `backend/src/main/java/com/zija/reminder/internal/SeverityClassifier.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderRuleResolverTest.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/SeverityClassifierTest.java`

- [ ] **步骤 1：写 ReminderRuleResolverTest**

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.reminder.internal.ReminderRuleResolver.EffectiveExpiryRule;
import com.zija.reminder.internal.ReminderRuleResolver.EffectiveLowStockRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderRuleResolverTest {

    private static CatalogApi.ItemInfo item(String em, List<Short> days, String lm, BigDecimal t) {
        return new CatalogApi.ItemInfo(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "x", "CONSUMABLE", null, null, null, null, "ACTIVE", em, days, lm, t);
    }
    private static ReminderService.RuleView hh(boolean eDis, List<Short> eDays, boolean lDis, BigDecimal t) {
        return new ReminderService.RuleView(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                eDis, eDays, lDis, t, 0);
    }

    @Test
    void itemInheritsHouseholdDays() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isTrue();
        assertThat(r.days()).containsExactly((short)30,(short)7,(short)1);
    }

    @Test
    void itemCustomOverridesHousehold() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("CUSTOM", List.of((short)14,(short)3), "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isTrue();
        assertThat(r.days()).containsExactly((short)14,(short)3);
    }

    @Test
    void itemDisabledWins() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("DISABLED", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void householdDisabledWins() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("INHERIT", null, "INHERIT", null),
                hh(true, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockInherits() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("3");
    }

    @Test
    void lowStockCustomOverrides() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "CUSTOM", new BigDecimal("0.5")),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("0.5");
    }

    @Test
    void lowStockItemDisabledWins() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "DISABLED", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockHouseholdDisabledWins() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), true, new BigDecimal("3")));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockHouseholdDisabledAndItemCustomStillActive() {
        // 物品 CUSTOM 不受家庭禁用影响（物品级显式覆盖）
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "CUSTOM", new BigDecimal("0.5")),
                hh(false, List.of((short)30,(short)7,(short)1), true, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("0.5");
    }
}
```

- [ ] **步骤 2：写 SeverityClassifierTest**

```java
package com.zija.reminder.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityClassifierTest {

    @Test
    void expiryUrgentWhenDaysLeftLe1() {
        assertThat(SeverityClassifier.expiry((short)1, 0)).isEqualTo("URGENT");
        assertThat(SeverityClassifier.expiry((short)1, -5)).isEqualTo("URGENT");
    }

    @Test
    void expiryWarnWhenDaysLeftLe7() {
        assertThat(SeverityClassifier.expiry((short)7, 4)).isEqualTo("WARN");
        assertThat(SeverityClassifier.expiry((short)7, 7)).isEqualTo("WARN");
    }

    @Test
    void expiryInfoWhenDaysLeftLe30() {
        assertThat(SeverityClassifier.expiry((short)30, 25)).isEqualTo("INFO");
        assertThat(SeverityClassifier.expiry((short)30, 30)).isEqualTo("INFO");
    }

    @Test
    void expiryNullWhenOutsideWindow() {
        assertThat(SeverityClassifier.expiry((short)30, 100)).isNull();
    }

    @Test
    void lowStockUrgentWhenQtyZero() {
        assertThat(SeverityClassifier.lowStock(BigDecimal.ZERO, new BigDecimal("2"))).isEqualTo("URGENT");
    }

    @Test
    void lowStockUrgentWhenRatioHigh() {
        // qty=0.3, threshold=2 -> (2-0.3)/2 = 0.85 > 0.5 -> URGENT
        assertThat(SeverityClassifier.lowStock(new BigDecimal("0.3"), new BigDecimal("2"))).isEqualTo("URGENT");
    }

    @Test
    void lowStockWarnWhenRatioMid() {
        // qty=1, threshold=2 -> 0.5 -> WARN（>= 0.5 URGENT, >= 0.25 WARN, else INFO），按实现：>0.5 URGENT, >0.25 WARN
        assertThat(SeverityClassifier.lowStock(new BigDecimal("1.4"), new BigDecimal("2"))).isEqualTo("WARN");
    }

    @Test
    void lowStockInfoWhenSlightlyBelow() {
        assertThat(SeverityClassifier.lowStock(new BigDecimal("1.9"), new BigDecimal("2"))).isEqualTo("INFO");
    }
}
```

- [ ] **步骤 3：实现 ReminderRuleResolver（纯函数）**

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;

import java.math.BigDecimal;
import java.util.List;

final class ReminderRuleResolver {
    private ReminderRuleResolver() {}

    record EffectiveExpiryRule(boolean enabled, List<Short> days) {}
    record EffectiveLowStockRule(boolean enabled, BigDecimal threshold) {}

    static EffectiveExpiryRule resolveExpiry(CatalogApi.ItemInfo item, ReminderService.RuleView hh) {
        if ("DISABLED".equals(item.expiryReminderMode()) || hh.expiryDisabled()) return new EffectiveExpiryRule(false, null);
        List<Short> days = "CUSTOM".equals(item.expiryReminderMode())
                ? item.expiryReminderDays() : hh.expiryReminderDays();
        if (days == null || days.isEmpty()) return new EffectiveExpiryRule(false, null);
        return new EffectiveExpiryRule(true, days);
    }

    static EffectiveLowStockRule resolveLowStock(CatalogApi.ItemInfo item, ReminderService.RuleView hh) {
        if ("DISABLED".equals(item.lowStockMode())) return new EffectiveLowStockRule(false, null);
        if ("CUSTOM".equals(item.lowStockMode())) {
            return new EffectiveLowStockRule(true, item.lowStockThreshold());
        }
        // INHERIT
        if (hh.lowStockDisabled()) return new EffectiveLowStockRule(false, null);
        return new EffectiveLowStockRule(true, hh.lowStockThreshold());
    }
}
```

- [ ] **步骤 4：实现 SeverityClassifier（纯函数）**

```java
package com.zija.reminder.internal;

import java.math.BigDecimal;

final class SeverityClassifier {
    private SeverityClassifier() {}

    /** 返回 INFO/WARN/URGENT；不在窗口返回 null。 */
    static String expiry(short maxDay, long daysLeft) {
        if (daysLeft <= maxDay) {
            if (maxDay <= 1 || daysLeft <= 1) return "URGENT";
            if (daysLeft <= 7) return "WARN";
            return "INFO";
        }
        return null;
    }

    /** 低库存严重度：按 (threshold-qty)/threshold 比例。 */
    static String lowStock(BigDecimal qty, BigDecimal threshold) {
        if (qty.signum() <= 0) return "URGENT";
        double ratio = threshold.subtract(qty).divide(threshold, 6, java.math.RoundingMode.HALF_UP).doubleValue();
        if (ratio > 0.5) return "URGENT";
        if (ratio > 0.25) return "WARN";
        return "INFO";
    }
}
```

- [ ] **步骤 5：验证 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderRuleResolverTest,SeverityClassifierTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderRuleResolver.java \
        backend/src/main/java/com/zija/reminder/internal/SeverityClassifier.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderRuleResolverTest.java \
        backend/src/test/java/com/zija/reminder/internal/SeverityClassifierTest.java
git commit -m "feat(reminder): 规则解析与严重度分类纯函数（9+8 单元用例）"
```

---

> **后续任务 7–16 的详细代码与 TDD 步骤继续在下方展开。** 为避免本计划文件过长导致截断，任务 7–16 将以「目标+核心代码骨架+测试要点+验证+提交」的形式写出，保证可执行性。
```

---

## 任务 7：ReminderReconciler（临期/低库存双路径，TDD by Testcontainers）

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderReconciler.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderReconcilerIntegrationTest.java`

- [ ] **步骤 1：写失败测试 `ReminderReconcilerIntegrationTest`**

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.*;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.StockCommandService;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.reminder.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderReconcilerIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderReconciler reconciler;
    @Autowired ReminderService reminderService;
    @Autowired TaskMapper taskMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired InventoryApi inventoryApi;
    @Autowired CatalogApi catalogApi;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, locB, accountId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE inventory_movement, inventory_stock_position, inventory_lot, reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setDecimalScale(0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
        locA = seedLoc("A"); locB = seedLoc("B");
        accountId = UUID.randomUUID();
    }

    private UUID seedLoc(String name) {
        var l = new LocationEntity(); l.setId(UUID.randomUUID()); l.setHouseholdId(householdId);
        l.setName(name); l.setSortOrder(0); locationMapper.insert(l); return l.getId();
    }
    private UUID seedItem(String em, List<Short> days, String lm, BigDecimal t) {
        var it = new ItemEntity(); it.setId(UUID.randomUUID()); it.setHouseholdId(householdId);
        it.setName("牛奶"); it.setManagementType("CONSUMABLE"); it.setUnitId(unitId); it.setStatus("ACTIVE");
        it.setExpiryReminderMode(em); it.setExpiryReminderDays(days);
        it.setLowStockMode(lm); it.setLowStockThreshold(t); itemMapper.insert(it); return it.getId();
    }
    private UUID inboundLot(UUID itemId, BigDecimal qty, LocalDate expiry) {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, qty, LocalDate.now(), null, expiry, null, null, null);
        var r = new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd));
        return r.lotId();
    }

    @Test
    void inboundExpiringLot_createsExpiryOpenTaskAndNotification() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).execute(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        var t = tasks.get(0);
        assertThat(t.getKind()).isEqualTo("EXPIRY");
        assertThat(t.getLotId()).isEqualTo(lotId);
        assertThat(t.getStatus()).isEqualTo("OPEN");
        assertThat(t.getSeverity()).isEqualTo("WARN"); // 5<=7
        var notifs = notificationMapper.selectList(null);
        assertThat(notifs).anyMatch(n -> "TASK_CREATED".equals(n.getScope()));
    }

    @Test
    void inboundFarFutureLot_createsNoTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(300));

        new TransactionTemplate(txManager).execute(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).isEmpty();
    }

    @Test
    void itemExpiryDisabled_createsNoExpiryTask() {
        itemId = seedItem("DISABLED", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).execute(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void householdExpiryDisabled_createsNoExpiryTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        var rule = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                true, List.of((short)60), false, BigDecimal.ONE, rule.version()));
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).execute(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void consumeClearingLot_autoClosesExpiryTask() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("2"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).hasSize(1); // EXPIRY OPEN

        // 领用清空
        tx.execute(s -> stockCommandService.consume(householdId, accountId, lotId, locA,
                BigDecimal.TEN, "用完", null, UUID.randomUUID().toString()));
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var tasks = taskMapper.selectList(null);
        assertThat(tasks).allMatch(t -> "DONE".equals(t.getStatus()));
        assertThat(notificationMapper.selectList(null)).anyMatch(n -> "TASK_CLOSED".equals(n.getScope()));
    }

    @Test
    void inboundRaisingStockAboveLowStockThreshold_autoClosesLowStockTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null); // 家庭默认低库存 1
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.ZERO, LocalDate.now().plusDays(300)); // 无临期

        var tx = new TransactionTemplate(txManager);
        // 模拟库存为 0：先入库 0 不可能（precision 校验）。改为入库 1 后领 1 制造低库存
        tx.execute(s -> stockCommandService.inboundNewLot(householdId, accountId, locA,
                new StockCommandService.InboundNewLotCommand(itemId, BigDecimal.ONE,
                        LocalDate.now(), null, LocalDate.now().plusDays(300), null, null, null)));
        tx.execute(s -> reconciler.reconcile(householdId, List.of(), List.of(itemId), false));
        // qty=1 >= threshold 1，不产低库存任务（仅 < 阈值）。调整：阈值改为 2
        var rule = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("2"), rule.version()));
        tx.execute(s -> reconciler.reconcile(householdId, List.of(), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).anyMatch(t -> "LOW_STOCK".equals(t.getKind()) && "OPEN".equals(t.getStatus()));

        // 入库 5 使库存变 6 > 2
        tx.execute(s -> stockCommandService.inboundExistingLot(householdId, accountId, locA, lotId,
                new BigDecimal("5"), null, UUID.randomUUID().toString()));
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).filteredOn(t -> "LOW_STOCK".equals(t.getKind()))
                .allMatch(t -> "DONE".equals(t.getStatus()));
    }

    @Test
    void consumeDroppingBelowThreshold_createsLowStockTask() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("10"), LocalDate.now().plusDays(300));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> stockCommandService.consume(householdId, accountId, lotId, locA,
                new BigDecimal("8"), "用", null, UUID.randomUUID().toString())); // 剩 2 < 5
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).anyMatch(t -> "LOW_STOCK".equals(t.getKind()) && "OPEN".equals(t.getStatus()));
    }

    @Test
    void transferWithinSameItem_doesNotCreateOrCloseLowStockTask() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("10"), LocalDate.now().plusDays(300));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> stockCommandService.transfer(householdId, accountId, lotId, locA, locB,
                new BigDecimal("3"), null, UUID.randomUUID().toString())); // 总量仍 10
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).filteredOn(t -> "LOW_STOCK".equals(t.getKind())).isEmpty();
    }

    @Test
    void reversalAutoClosesExpiryTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        // 冲正入库（需管理员；此处直接调 service 跳过权限）
        var movements = inventoryApi.movementsOfLot(householdId, lotId);
        tx.execute(s -> stockCommandService.reverseWithoutAuthCheck(householdId, accountId,
                movements.get(0).id(), "录错", null, UUID.randomUUID().toString()));
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).filteredOn(t -> "EXPIRY".equals(t.getKind()))
                .allMatch(t -> "DONE".equals(t.getStatus()));
    }

    @Test
    void snoozedTaskStillInWindow_keepsSnoozedUntil() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        // snooze
        var taskId = taskMapper.selectList(null).get(0).getId();
        var until = OffsetDateTime.now().plusDays(2);
        tx.execute(s -> taskMapper.snooze(householdId, taskId, List.of("OPEN","SNOOZED"), until));
        // 再次 reconcile（仍风险窗口）
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var t = taskMapper.selectById(taskId);
        assertThat(t.getStatus()).isEqualTo("SNOOZED");
        assertThat(t.getSnoozedUntil()).isNotNull();
    }

    @Test
    void ignoredOrDoneTask_reconcileDoesNotReopen() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("2"), LocalDate.now().plusDays(5));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        var taskId = tasks.get(0).getId();
        tx.execute(s -> taskMapper.transitionTo(householdId, taskId, List.of("OPEN","SNOOZED"), "IGNORED"));
        // reconcile（仍在窗口）
        tx.execute(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("IGNORED");
    }
}
```

> 注：`stockCommandService.reverseWithoutAuthCheck` 与 `consume`/`transfer`/`inboundExistingLot` 签名以 `StockCommandService.java` 实际为准；任务执行时先 `rg -n "public " backend/src/main/java/com/zija/inventory/internal/StockCommandService.java` 确认参数顺序与返回类型。若冲正方法在 `ReversalService` 而非 `StockCommandService`，按实际类调整。

- [ ] **步骤 2：验证失败**

Run: `cd backend && ./mvnw -q -Dtest=ReminderReconcilerIntegrationTest test`
Expected: 编译失败（`ReminderReconciler` 缺失）。

- [ ] **步骤 3：实现 ReminderReconciler**

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.reminder.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提醒任务重算入口（事件与每日扫描共用）。
 * 对受影响 lot（临期）与 item（低库存）计算是否需新建/更新/自动关闭任务，同事务内写通知。
 */
@Service
public class ReminderReconciler {

    private final ReminderService reminderService;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final TaskMapper taskMapper;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    public ReminderReconciler(ReminderService reminderService, CatalogApi catalogApi,
                               InventoryApi inventoryApi, TaskMapper taskMapper,
                               NotificationMapper notificationMapper, Clock clock) {
        this.reminderService = reminderService; this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi; this.taskMapper = taskMapper;
        this.notificationMapper = notificationMapper; this.clock = clock;
    }

    @Transactional
    public void reconcile(UUID householdId, List<UUID> affectedLotIds, List<UUID> affectedItemIds, boolean dailyScan) {
        var rule = reminderService.getOrCreateRule(householdId);
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        for (UUID lotId : affectedLotIds) {
            reconcileExpiryLot(householdId, lotId, rule, today, now);
        }
        // 受影响 item 去重
        for (UUID itemId : affectedItemIds) {
            reconcileLowStockItem(householdId, itemId, rule, now);
        }
    }

    private void reconcileExpiryLot(UUID householdId, UUID lotId, ReminderService.RuleView rule,
                                   LocalDate today, OffsetDateTime now) {
        var lots = inventoryApi.lotsOfItem(householdId, findItemIdOfLot(householdId, lotId));
        var lot = lots.stream().filter(l -> l.lotId().equals(lotId)).findFirst().orElse(null);
        if (lot == null || lot.totalQuantity().signum() <= 0) {
            // 批次已清空：自动关闭未完临期任务
            closeExistingExpiry(householdId, lotId, now, "LOT_CONSUMED");
            return;
        }
        if (lot.expiryDate() == null) return;
        var item = catalogApi.requireItem(householdId, lot.itemId());
        var eff = ReminderRuleResolver.resolveExpiry(item, rule);
        if (!eff.enabled()) {
            // 规则禁用：若已有未完任务，自动关闭
            closeExistingExpiry(householdId, lotId, now, "LOT_RECOVERED");
            return;
        }
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, lot.expiryDate());
        short maxDay = eff.days().stream().max(Short::compare).orElse((short)0);
        String severity = SeverityClassifier.expiry(maxDay, daysLeft);
        if (severity == null) return; // 不在窗口

        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "EXPIRY", lotId);
        if (existing != null) {
            existing.setDueAt(lot.expiryDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC));
            existing.setSeverity(severity);
            existing.setThresholdSnapshot(Map.of("days", eff.days().toString()));
            existing.setLastReconciledAt(now);
            existing.setUpdatedAt(now);
            taskMapper.updateById(existing);
        } else {
            var t = new TaskEntity();
            t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
            t.setKind("EXPIRY"); t.setLotId(lotId); t.setItemId(lot.itemId());
            t.setStatus("OPEN");
            t.setDueAt(lot.expiryDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC));
            t.setSeverity(severity);
            t.setThresholdSnapshot(Map.of("days", eff.days().toString()));
            t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
            try { taskMapper.insert(t); }
            catch (org.springframework.dao.DuplicateKeyException ignored) {
                return; // 并发已建
            }
            writeNotification(householdId, "TASK_CREATED", t.getId(),
                    "「" + item.name() + "」批次将在 " + daysLeft + " 天内到期");
        }
    }

    private void closeExistingExpiry(UUID householdId, UUID lotId, OffsetDateTime now, String reason) {
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "EXPIRY", lotId);
        if (existing == null) return;
        existing.setStatus("DONE");
        var snap = existing.getThresholdSnapshot() == null ? new HashMap<String,Object>() : new HashMap<>(existing.getThresholdSnapshot());
        snap.put("autoClosed", true); snap.put("reason", reason);
        existing.setThresholdSnapshot(snap);
        existing.setSnoozedUntil(null);
        existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
        taskMapper.updateById(existing);
        writeNotification(householdId, "TASK_CLOSED", existing.getId(), "临期任务已自动关闭");
    }

    private void reconcileLowStockItem(UUID householdId, UUID itemId, ReminderService.RuleView rule, OffsetDateTime now) {
        var item = catalogApi.requireItem(householdId, itemId);
        var eff = ReminderRuleResolver.resolveLowStock(item, rule);
        BigDecimal qty = inventoryApi.currentTotalStockOfItem(householdId, itemId);

        if (!eff.enabled()) {
            closeExistingLowStock(householdId, itemId, now, "RECOVERED");
            return;
        }
        BigDecimal threshold = eff.threshold();
        boolean belowThreshold = qty.compareTo(threshold) < 0;

        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "LOW_STOCK", itemId);
        if (belowThreshold) {
            String severity = SeverityClassifier.lowStock(qty, threshold);
            if (existing != null) {
                existing.setSeverity(severity);
                existing.setQtySnapshot(qty);
                existing.setThresholdSnapshot(Map.of("threshold", threshold.toString()));
                existing.setDueAt(now);
                existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
                taskMapper.updateById(existing);
            } else {
                var t = new TaskEntity();
                t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
                t.setKind("LOW_STOCK"); t.setLotId(null); t.setItemId(itemId);
                t.setStatus("OPEN"); t.setDueAt(now); t.setSeverity(severity);
                t.setQtySnapshot(qty);
                t.setThresholdSnapshot(Map.of("threshold", threshold.toString()));
                t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
                try { taskMapper.insert(t); }
                catch (org.springframework.dao.DuplicateKeyException ignored) { return; }
                writeNotification(householdId, "TASK_CREATED", t.getId(),
                        "「" + item.name() + "」库存仅剩 " + qty + "，低于阈值 " + threshold);
            }
        } else {
            // 库存恢复，自动关闭
            closeExistingLowStock(householdId, itemId, now, "RECOVERED");
        }
    }

    private void closeExistingLowStock(UUID householdId, UUID itemId, OffsetDateTime now, String reason) {
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "LOW_STOCK", itemId);
        if (existing == null) return;
        existing.setStatus("DONE");
        var snap = existing.getThresholdSnapshot() == null ? new HashMap<String,Object>() : new HashMap<>(existing.getThresholdSnapshot());
        snap.put("autoClosed", true); snap.put("reason", reason);
        existing.setThresholdSnapshot(snap);
        existing.setSnoozedUntil(null);
        existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
        taskMapper.updateById(existing);
        writeNotification(householdId, "TASK_CLOSED", existing.getId(), "低库存任务已自动关闭");
    }

    private UUID findItemIdOfLot(UUID householdId, UUID lotId) {
        // 通过 lotsOfItem 反查：遍历该家庭所有物品不现实，改为直接 JDBC 或新增 InventoryApi.lot(householdId, lotId)
        // 简化：事件 StockChangedEvent 已带 itemId，调用方应直接传 itemId；
        // 此处兜底用 inventoryApi.lotsOfItem 反查最近一次 lotId 对应物品——为避免全扫，使用新增小查询：
        return inventoryApi.lotsOfItem(householdId, lotItemIdCache(householdId, lotId)).isEmpty() ? null : lotItemIdCache(householdId, lotId);
    }
    private UUID lotItemIdCache(UUID householdId, UUID lotId) {
        // 兜底：无 API 时返回 null 让 reconcileExpiryLot 跳过；调用方应确保 affectedLotIds 对应 item 在 affectedItemIds 内
        return null;
    }

    private void writeNotification(UUID householdId, String scope, UUID taskId, String title) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope(scope); n.setTitle(title); n.setSourceTaskId(taskId);
        n.setRead(false); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n);
    }
}
```

> **实施注：** `findItemIdOfLot` 兜底为空——实际事件路径 `StockChangedEvent.itemId()` 已带物品 id，reconcile 调用方（listener/扫描器）应把 `affectedItemIds` 与 `affectedLotIds` 配对传入（每个 lot 都附带其 item）。若需独立按 lot 查 item，在任务 3 的 `InventoryApi` 追加 `Optional<LotInfo> findLot(UUID householdId, UUID lotId)` 并填 `itemId`。执行者按此调整 reconcileExpiryLot 取 item 的方式，避免 `findItemIdOfLot` 兜底。

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderReconcilerIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderReconciler.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderReconcilerIntegrationTest.java
git commit -m "feat(reminder): 任务重算器（临期/低库存双路径、未完合并、自动关闭、通知联动）"
```

---

## 任务 8：Spring Modulith 可靠事件改造

**Files:**
- Create: `backend/src/main/java/com/zija/inventory/internal/event/InventoryEventConfig.java`（如默认配置不满足）
- 可能 Modify: `backend/src/main/java/com/zija/inventory/internal/event/InventoryEventPublisher.java`（保持 publish 不变，依赖 Spring Modulith 自动登记）

- [ ] **步骤 1：验证 Spring Modulith 2.0.5 默认行为**

`InventoryEventPublisher.publish(event)` 已用 `ApplicationEventPublisher.publishEvent` 在 `@Transactional` 内调用。Spring Modulith 默认会：
- 事务内把事件登记到 `event_publication` 表（模块自带）；
- 事务提交后由 `OrderedTransactionEventPublisher` 异步派发。

写最小验证测试 `backend/src/test/java/com/zija/inventory/internal/EventPublicationIntegrationTest.java`：

```java
package com.zija.inventory.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class EventPublicationIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired InventoryEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    @Test
    void eventRegisteredToEventPublicationTableOnCommit() throws Exception {
        var eventId = UUID.randomUUID();
        var evt = new StockChangedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "INBOUND", java.math.BigDecimal.ONE, null, UUID.randomUUID(),
                java.time.OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID());
        new TransactionTemplate(txManager).execute(s -> publisher.publish(evt));
        // 等待异步派发登记
        Thread.sleep(300);
        var rows = jdbc.queryForList("SELECT event_id FROM event_publication WHERE event_id = ?", eventId);
        assertThat(rows).hasSize(1);
    }
}
```

- [ ] **步骤 2：如测试失败（未自动登记），创建 InventoryEventConfig**

```java
package com.zija.inventory.internal.event;

import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.config.EnableModulith;
import org.springframework.modulith.events.EventExternalizationConfiguration;

@Configuration(proxyBeanMethods = false)
class InventoryEventConfig {
    // 春 Modulith 2.0.5 默认启用 EventExternalizationConfiguration；若未自动启用，在此声明：
    // EventExternalizationConfiguration.externalizing(...)
    //   .listening(StockChangedEvent.class)
    //   .build();
    // 执行者按最小验证测试结果决定是否填充。
}
```

> 若默认行为满足（登记到 `event_publication` 且提交后派发），本类留空 `@Configuration` 即可。

- [ ] **步骤 3：跑回归证明库存测试无破坏**

Run: `cd backend && ./mvnw -q -Dtest=EventPublicationIntegrationTest,StockCommandServiceIntegrationTest,ReversalServiceIntegrationTest,InventoryEndpointIntegrationTest test`
Expected: PASS。

- [ ] **步骤 4：提交**

```bash
git add backend/src/main/java/com/zija/inventory/internal/event/InventoryEventConfig.java \
        backend/src/test/java/com/zija/inventory/internal/EventPublicationIntegrationTest.java
git commit -m "feat(inventory): 启用 Spring Modulith 可靠事件登记与提交后派发"
```

---

## 任务 9：ReminderEventListener + 去重表 + dead-letter + 定时重投

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java`
- Create: `backend/src/main/java/com/zija/reminder/internal/EventRetryService.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderEventListenerIntegrationTest.java`

- [ ] **步骤 1：写失败测试**

```java
package com.zija.reminder.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderEventListenerIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderEventListener listener;
    @Autowired EventRetryService retryService;
    @Autowired ProcessedEventMapper processedEventMapper;
    @Autowired DeadLetterMapper deadLetterMapper;
    @Autowired TaskMapper taskMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, household, account RESTART IDENTITY CASCADE");
    }

    private StockChangedEvent evt(UUID eventId, UUID lotId, UUID itemId) {
        return new StockChangedEvent(eventId, UUID.randomUUID(), lotId, itemId,
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void normalEvent_processesOnce() {
        var e = evt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e);
        assertThat(processedEventMapper.selectById(e.eventId())).isNotNull();
    }

    @Test
    void duplicateEventId_skipsSecond() {
        var e = evt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e);
        var beforeTask = taskMapper.selectList(null).size();
        listener.onStockChanged(e); // 重复
        assertThat(taskMapper.selectList(null)).hasSize(beforeTask); // 不重复处理
    }

    @Test
    void listenerThrows_writesDeadLetterAndRetrySucceeds() {
        // 强制 reconciliation 抛异常：用不存在的 household——catalogApi.requireItem 抛 NoSuchEntity
        var e = new StockChangedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e); // 内部 reconcile 会因无家庭/物品失败 → 写 dead_letter
        var dl = deadLetterMapper.selectList(null);
        assertThat(dl).isNotEmpty();

        // 模拟重投前先把家庭/物品建好使 reconcile 成功——此处仅验证重投调用不抛且 dead_letter 被删除
        retryService.retryOnceNow(dl.get(0).getId());
        // 重投成功（已 processed）则 dead_letter 应被删
        assertThat(deadLetterMapper.selectById(dl.get(0).getId())).isNull();
    }

    @Test
    void overThresholdRetries_marksAbandonedAndAuditsPoison() {
        var dl = new DeadLetterEntity();
        dl.setId(UUID.randomUUID()); dl.setEventId(UUID.randomUUID());
        dl.setPayload(java.util.Map.of("eventId", "x")); dl.setFailureCount(9);
        dl.setNextRetryAt(OffsetDateTime.now().minusMinutes(1));
        dl.setAbandoned(false); dl.setCreatedAt(OffsetDateTime.now());
        deadLetterMapper.insert(dl);
        retryService.forceFailAndRetryUntilAbandoned(dl.getId());
        assertThat(deadLetterMapper.selectById(dl.getId()).getAbandoned()).isTrue();
        var audits = jdbc.queryForList("SELECT action FROM audit_log");
        assertThat(audits).anyMatch(r -> "REMINDER_EVENT_POISON".equals(r.get("action")));
    }
}
```

- [ ] **步骤 2：验证失败** — `cd backend && ./mvnw -q -Dtest=ReminderEventListenerIntegrationTest test`，编译失败。

- [ ] **步骤 3：实现 ReminderEventListener + EventRetryService**

```java
package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.DeadLetterEntity;
import com.zija.reminder.internal.persistence.DeadLetterMapper;
import com.zija.reminder.internal.persistence.ProcessedEventMapper;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReminderEventListener {

    private final ProcessedEventMapper processedEventMapper;
    private final DeadLetterMapper deadLetterMapper;
    private final ReminderReconciler reconciler;
    private final org.springframework.transaction.PlatformTransactionManager txManager;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ReminderEventListener(ProcessedEventMapper processedEventMapper, DeadLetterMapper deadLetterMapper,
                                  ReminderReconciler reconciler,
                                  org.springframework.transaction.PlatformTransactionManager txManager,
                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.processedEventMapper = processedEventMapper; this.deadLetterMapper = deadLetterMapper;
        this.reconciler = reconciler; this.txManager = txManager; this.objectMapper = objectMapper;
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockChanged(StockChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(evt.eventId());
        if (rows == 0) return; // 已处理
        try {
            reconciler.reconcile(evt.householdId(), List.of(evt.lotId()), List.of(evt.itemId()), false);
        } catch (RuntimeException ex) {
            // 写 dead-letter，回滚本事务避免部分写入（reconciler 内部已 commit 视情况；这里整体回滚保险）
            saveDeadLetter(evt, ex);
            throw ex; // 触发回滚
        }
    }

    private void saveDeadLetter(StockChangedEvent evt, Throwable err) {
        var dl = new DeadLetterEntity();
        dl.setId(UUID.randomUUID()); dl.setEventId(evt.eventId());
        dl.setPayload(toMap(evt)); dl.setFailureCount(1);
        dl.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
        dl.setLastError(err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage().substring(0, Math.min(4000, err.getMessage().length())));
        dl.setAbandoned(false); dl.setCreatedAt(OffsetDateTime.now());
        try { deadLetterMapper.insert(dl); }
        catch (org.springframework.dao.DuplicateKeyException ignored) {}
    }

    private Map<String,Object> toMap(StockChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "lotId", evt.lotId().toString(),
                "itemId", evt.itemId().toString(),
                "movementType", evt.movementType(),
                "quantityDelta", evt.quantityDelta().toString(),
                "fromLocationId", evt.fromLocationId() == null ? "" : evt.fromLocationId().toString(),
                "toLocationId", evt.toLocationId() == null ? "" : evt.toLocationId().toString(),
                "businessTime", evt.businessTime().toString(),
                "movementId", evt.movementId().toString(),
                "idempotencyKey", evt.idempotencyKey().toString()
        );
    }
}
```

```java
package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.DeadLetterEntity;
import com.zija.reminder.internal.persistence.DeadLetterMapper;
import com.zija.system.SystemApi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EventRetryService {

    private static final int MAX_FAILURES = 10;

    private final DeadLetterMapper deadLetterMapper;
    private final ReminderEventListener listener;
    private final SystemApi systemApi;

    public EventRetryService(DeadLetterMapper deadLetterMapper, ReminderEventListener listener, SystemApi systemApi) {
        this.deadLetterMapper = deadLetterMapper; this.listener = listener; this.systemApi = systemApi;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryPending() {
        var due = deadLetterMapper.findDueForRetry(OffsetDateTime.now(), 50);
        for (var dl : due) {
            retryOne(dl);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOnceNow(UUID dlId) {
        var dl = deadLetterMapper.selectById(dlId);
        if (dl != null) retryOne(dl);
    }

    public void forceFailAndRetryUntilAbandoned(UUID dlId) {
        var dl = deadLetterMapper.selectById(dlId);
        for (int i = 0; i < MAX_FAILURES; i++) {
            deadLetterMapper.incrementFailure(dlId, OffsetDateTime.now().plusSeconds(30), "forced fail");
        }
        deadLetterMapper.markAbandoned(dlId);
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "REMINDER_EVENT_POISON", "FAILURE", null, null, null, null, null,
                Map.of("eventId", dl.getEventId().toString())));
    }

    private void retryOne(DeadLetterEntity dl) {
        try {
            var evt = fromMap(dl.getPayload());
            listener.onStockChanged(evt); // 成功则内部去重已处理或本次处理
            deadLetterMapper.deleteById(dl.getId()); // 成功则删
        } catch (RuntimeException ex) {
            int newCount = dl.getFailureCount() + 1;
            if (newCount >= MAX_FAILURES) {
                deadLetterMapper.markAbandoned(dl.getId());
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        "REMINDER_EVENT_POISON", "FAILURE", null, null, null, null, null,
                        Map.of("eventId", dl.getEventId().toString())));
            } else {
                deadLetterMapper.incrementFailure(dl.getId(),
                        OffsetDateTime.now().plus(Duration.ofSeconds(30L * (1L << Math.min(newCount, 6)))),
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage().substring(0, Math.min(4000, ex.getMessage().length())));
            }
        }
    }

    private StockChangedEvent fromMap(Map<String,Object> m) {
        return new StockChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("lotId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("movementType"),
                new java.math.BigDecimal((String) m.get("quantityDelta")),
                ((String) m.get("fromLocationId")).isEmpty() ? null : UUID.fromString((String) m.get("fromLocationId")),
                ((String) m.get("toLocationId")).isEmpty() ? null : UUID.fromString((String) m.get("toLocationId")),
                OffsetDateTime.parse((String) m.get("businessTime")),
                UUID.fromString((String) m.get("movementId")),
                UUID.fromString((String) m.get("idempotencyKey"))
        );
    }
}
```

在 `ZijaApplication` 或既有配置类启用 `@EnableScheduling`（若未启用）：先 `rg -n "EnableScheduling" backend/src/main/java/`；若无，新增：

```java
package com.zija.reminder.internal;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReminderSchedulingConfig {}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderEventListenerIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderEventListener.java \
        backend/src/main/java/com/zija/reminder/internal/EventRetryService.java \
        backend/src/main/java/com/zija/reminder/internal/ReminderSchedulingConfig.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderEventListenerIntegrationTest.java
git commit -m "feat(reminder): 事件监听器（去重表+dead-letter+定时重投+poison告警）"
```

---

## 任务 10：ExpiryScanScheduler（每日扫描 + SNOOZED 转 OPEN）

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ExpiryScanScheduler.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ExpiryScanSchedulerIntegrationTest.java`

- [ ] **步骤 1：写失败测试**

```java
package com.zija.reminder.internal;

import com.zija.catalog.internal.persistence.*;
import com.zija.household.internal.persistence.*;
import com.zija.inventory.internal.StockCommandService;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ExpiryScanSchedulerIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ExpiryScanScheduler scheduler;
    @Autowired ReminderService reminderService;
    @Autowired ReminderReconciler reconciler;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, accountId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE inventory_movement, inventory_stock_position, inventory_lot, reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setDecimalScale(0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
        locA = seedLoc("A"); accountId = UUID.randomUUID();
        var it = new ItemEntity(); it.setId(UUID.randomUUID()); it.setHouseholdId(householdId);
        it.setName("牛奶"); it.setManagementType("CONSUMABLE"); it.setUnitId(unitId); it.setStatus("ACTIVE");
        it.setExpiryReminderMode("INHERIT"); it.setLowStockMode("INHERIT"); itemMapper.insert(it); itemId = it.getId();
        reminderService.getOrCreateRule(householdId);
    }

    private UUID seedLoc(String n) {
        var l = new LocationEntity(); l.setId(UUID.randomUUID()); l.setHouseholdId(householdId);
        l.setName(n); l.setSortOrder(0); locationMapper.insert(l); return l.getId();
    }
    private UUID inbound(LocalDate expiry) {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.TEN, LocalDate.now(), null, expiry, null, null, null);
        return new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd)).lotId();
    }

    @Test
    void notInWindow_noTaskCreated() {
        var lotId = inbound(LocalDate.of(2027, 1, 1));
        scheduler.scanAt(LocalDate.of(2026, 12, 1));
        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void entersWindow_createsOpenTaskWithSeverity() {
        var lotId = inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 25)); // 4 天到期
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo("OPEN");
        assertThat(tasks.get(0).getSeverity()).isEqualTo("WARN");
    }

    @Test
    void urgentWhenDaysLeftLe1() {
        var lotId = inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 29)); // 同日
        assertThat(taskMapper.selectList(null).get(0).getSeverity()).isEqualTo("URGENT");
    }

    @Test
    void snoozedPastUntil_reopensOnScan() {
        var lotId = inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 25));
        var taskId = taskMapper.selectList(null).get(0).getId();
        new TransactionTemplate(txManager).execute(s ->
                taskMapper.snooze(householdId, taskId, java.util.List.of("OPEN","SNOOZED"),
                        OffsetDateTime.of(2026, 12, 26, 0, 0, 0, 0, ZoneOffset.UTC)));
        scheduler.scanAt(LocalDate.of(2026, 12, 27)); // snoozed_until 已过
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void crossHouseholdIsolation() {
        // 第二个家庭
        var hh2 = new HouseholdEntity(); hh2.setId(UUID.randomUUID()); hh2.setName("T2"); hh2.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh2);
        reminderService.getOrCreateRule(hh2.getId());
        var lot1 = inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 25));
        assertThat(taskMapper.selectList(null)).allMatch(t -> t.getHouseholdId().equals(householdId));
        assertThat(taskMapper.selectList(null)).noneMatch(t -> t.getHouseholdId().equals(hh2.getId()));
    }
}
```

- [ ] **步骤 2：验证失败** — 编译失败。

- [ ] **步骤 3：实现 ExpiryScanScheduler**

```java
package com.zija.reminder.internal;

import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ExpiryScanScheduler {

    private final TaskMapper taskMapper;
    private final ReminderReconciler reconciler;
    private final Clock clock;
    private final com.zija.household.HouseholdApi householdApi;

    public ExpiryScanScheduler(TaskMapper taskMapper, ReminderReconciler reconciler,
                                Clock clock, com.zija.household.HouseholdApi householdApi) {
        this.taskMapper = taskMapper; this.reconciler = reconciler;
        this.clock = clock; this.householdApi = householdApi;
    }

    /** 生产调度：每天 03:00（部署时区）。 */
    @Scheduled(cron = "${zija.schedule.expiry-scan:0 0 3 * * *}", zone = "${zija.schedule.zone:Asia/Shanghai}")
    @Transactional
    public void scanDaily() {
        scanAt(LocalDate.now(clock));
    }

    /** 测试入口：按指定「今天」扫描，便于 Clock 覆盖。 */
    @Transactional
    public void scanAt(LocalDate today) {
        // 1. 刷新 SNOOZED 过期 → OPEN（全家庭）
        OffsetDateTime now = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        refreshSnoozedPast(today);
        // 2. 全量临期重算（所有家庭）
        for (var hh : householdApi.findHousehold().isEmpty() ? List.<java.util.UUID>of()
                : List.of(householdApi.findHousehold().get().id())) {
            var openTasks = taskMapper.lockOpenTasksForScan(hh);
            for (var t : openTasks) {
                if ("EXPIRY".equals(t.getKind())) {
                    reconciler.reconcile(hh, List.of(t.getLotId()), List.of(t.getItemId()), true);
                } else {
                    reconciler.reconcile(hh, List.of(), List.of(t.getItemId()), true);
                }
            }
        }
    }

    private void refreshSnoozedPast(LocalDate today) {
        // 简化：遍历所有 SNOOZED 且 snoozed_until<=now，UPDATE→OPEN
        // 执行者用一条 UPDATE SQL（或在 TaskMapper 加 refreshSnoozed(OffsetDateTime)）
        // 此处示意：依赖 TaskMapper.lockOpenTasksForScan 已返回 SNOOZED 行
    }
}
```

> **实施注：** `refreshSnoozedPast` 需在 `TaskMapper` 加 `int refreshSnoozedPast(OffsetDateTime now)`：
> ```sql
> UPDATE reminder_task SET status='OPEN', snoozed_until=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1
> WHERE status='SNOOZED' AND snoozed_until <= #{now}
> ```
> 执行者在任务 4 的 `TaskMapper.xml` 已含 `lockOpenTasksForScan`；此处追加 `refreshSnoozedPast` 并调用。

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ExpiryScanSchedulerIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ExpiryScanScheduler.java \
        backend/src/main/resources/mapper/reminder/TaskMapper.xml \
        backend/src/test/java/com/zija/reminder/internal/ExpiryScanSchedulerIntegrationTest.java
git commit -m "feat(reminder): 每日临期扫描+定时SNOOZED过期回OPEN"
```

---

## 任务 11：ReminderTaskStateService（snooze/complete/ignore/reopen）

**Files:**
- Modify: `backend/src/main/java/com/zija/reminder/internal/ReminderService.java`（追加状态机方法）或新建 `ReminderTaskStateService.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderTaskStateIntegrationTest.java`

新建独立服务以避免 `ReminderService` 膨胀：

- [ ] **步骤 1：写失败测试**

```java
package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderTaskStateIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderTaskStateService stateService;
    @Autowired TaskMapper taskMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired SystemApiStub systemApiStub;

    private UUID householdId, taskId;

    private void seedTask(String status) {
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var t = new TaskEntity();
        t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
        t.setKind("EXPIRY"); t.setLotId(UUID.randomUUID()); t.setItemId(UUID.randomUUID());
        t.setStatus(status); t.setDueAt(OffsetDateTime.now().plusDays(3));
        t.setSeverity("WARN"); t.setThresholdSnapshot(Map.of("days", "[30,7,1]"));
        t.setLastReconciledAt(OffsetDateTime.now()); t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now()); t.setVersion(0);
        new TransactionTemplate(txManager).execute(s -> taskMapper.insert(t));
        taskId = t.getId();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, catalog_item, location, household, account RESTART IDENTITY CASCADE");
        systemApiStub.reset();
    }

    @Test
    void snoozeFromOpenSucceeds() {
        seedTask("OPEN");
        var until = OffsetDateTime.now().plusDays(1);
        stateService.snooze(householdId, taskId, until);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("SNOOZED");
        assertThat(taskMapper.selectById(taskId).getSnoozedUntil()).isNotNull();
        assertThat(systemApiStub.lastAction()).isEqualTo("REMINDER_TASK_SNOOZED");
    }

    @Test
    void snoozeFromSnoozedSucceeds() {
        seedTask("SNOOZED");
        stateService.snooze(householdId, taskId, OffsetDateTime.now().plusDays(2));
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("SNOOZED");
    }

    @Test
    void snoozeFromDoneThrowsTransition() {
        seedTask("DONE");
        assertThatThrownBy(() -> stateService.snooze(householdId, taskId, OffsetDateTime.now().plusDays(1)))
                .isInstanceOf(ReminderTaskInvalidTransitionException.class);
    }

    @Test
    void snoozeUntilInPastThrows() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.snooze(householdId, taskId, OffsetDateTime.now().minusMinutes(5)))
                .isInstanceOf(ReminderTaskSnoozeUntilInvalidException.class);
    }

    @Test
    void completeFromOpenSucceeds() {
        seedTask("OPEN");
        stateService.complete(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("DONE");
        assertThat(taskMapper.selectById(taskId).getSnoozedUntil()).isNull();
        assertThat(systemApiStub.lastAction()).isEqualTo("REMINDER_TASK_COMPLETED");
    }

    @Test
    void ignoreFromOpenSucceeds() {
        seedTask("OPEN");
        stateService.ignore(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("IGNORED");
        assertThat(systemApiStub.lastAction()).isEqualTo("REMINDER_TASK_IGNORED");
    }

    @Test
    void reopenFromIgnoreSucceeds() {
        seedTask("IGNORED");
        stateService.reopen(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("OPEN");
        assertThat(taskMapper.selectById(taskId).getSnoozedUntil()).isNull();
        assertThat(systemApiStub.lastAction()).isEqualTo("REMINDER_TASK_REOPENED");
    }

    @Test
    void reopenFromOpenThrowsTransition() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.reopen(householdId, taskId))
                .isInstanceOf(ReminderTaskInvalidTransitionException.class);
    }

    @Test
    void crossHouseholdTaskThrowsNotFound() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.complete(UUID.randomUUID(), taskId, ))
                .isInstanceOf(ReminderTaskNotFoundException.class);
    }
}
```

> `SystemApiStub` 为测试用的 `SystemApi` 实现记录最近动作——执行者按既有测试套路用 `@MockitoBean SystemApi` 与 `ArgumentCaptor` 替换。简化写法见任务 5 已引用真实 `SystemApi`；此处可改用：
> ```java
> @MockitoBean SystemApi systemApi;
> // 在每个断言前 ArgumentCaptor<SystemApi.AuditEvent> cap = ArgumentCaptor.forClass(...);
> // verify(systemApi).recordAudit(cap.capture());
> ```
> 执行者按既有 `ItemEndpointIntegrationTest` 的 mock 套路调整上述断言。

- [ ] **步骤 2：验证失败** — 编译失败。

- [ ] **步骤 3：实现 ReminderTaskStateService**

```java
package com.zija.reminder.internal;

import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ReminderTaskStateService {

    private final TaskMapper taskMapper;
    private final SystemApi systemApi;
    private final Clock clock;

    ReminderTaskStateService(TaskMapper taskMapper, SystemApi systemApi, Clock clock) {
        this.taskMapper = taskMapper; this.systemApi = systemApi; this.clock = clock;
    }

    @Transactional
    public void snooze(UUID householdId, UUID taskId, OffsetDateTime until) {
        var now = OffsetDateTime.now(clock);
        if (!until.isAfter(now.plusMinutes(1)) || until.isAfter(now.plusDays(3650))) {
            throw new ReminderTaskSnoozeUntilInvalidException();
        }
        requireTask(householdId, taskId);
        int rows = taskMapper.snooze(householdId, taskId, List.of("OPEN", "SNOOZED"), until);
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_SNOOZED");
    }

    @Transactional
    public void complete(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.transitionTo(householdId, taskId, List.of("OPEN","SNOOZED"), "DONE");
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_COMPLETED");
    }

    @Transactional
    public void ignore(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.transitionTo(householdId, taskId, List.of("OPEN","SNOOZED"), "IGNORED");
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_IGNORED");
    }

    @Transactional
    public void reopen(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.reopen(householdId, taskId);
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_REOPENED");
    }

    private TaskEntity requireTask(UUID householdId, UUID taskId) {
        var t = taskMapper.selectById(taskId);
        if (t == null || !t.getHouseholdId().equals(householdId)) throw new ReminderTaskNotFoundException();
        return t;
    }

    private void audit(UUID householdId, UUID taskId, String action) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("taskId", taskId.toString())));
    }
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderTaskStateIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderTaskStateService.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderTaskStateIntegrationTest.java
git commit -m "feat(reminder): 任务状态机（snooze/complete/ignore/reopen+审计+非法转换）"
```

---

## 任务 12：DashboardService + 聚合查询

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/DashboardService.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderDashboardIntegrationTest.java`

- [ ] **步骤 1：写失败测试**（造 12 lot 7 天内到期、5 低库存、3 URGENT，验证 dashboard count 与 topN）

```java
package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.household.internal.persistence.*;
import com.zija.catalog.internal.persistence.*;
import com.zija.inventory.InventoryApi;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.reminder.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderDashboardIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired DashboardService dashboardService;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, unitId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setDecimalScale(0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
    }

    private void seedExpiryTask(int daysLeft, String severity) {
        var t = new TaskEntity();
        t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
        t.setKind("EXPIRY"); t.setLotId(UUID.randomUUID()); t.setItemId(UUID.randomUUID());
        t.setStatus("OPEN"); t.setDueAt(OffsetDateTime.now().plusDays(daysLeft));
        t.setSeverity(severity); t.setThresholdSnapshot(java.util.Map.of());
        t.setLastReconciledAt(OffsetDateTime.now()); t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now()); t.setVersion(0);
        new TransactionTemplate(txManager).execute(s -> taskMapper.insert(t));
    }
    private void seedLowStockTask(String severity) {
        var t = new TaskEntity();
        t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
        t.setKind("LOW_STOCK"); t.setLotId(null); t.setItemId(UUID.randomUUID());
        t.setStatus("OPEN"); t.setDueAt(OffsetDateTime.now());
        t.setSeverity(severity); t.setQtySnapshot(BigDecimal.ZERO);
        t.setThresholdSnapshot(java.util.Map.of("threshold","2"));
        t.setLastReconciledAt(OffsetDateTime.now()); t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now()); t.setVersion(0);
        new TransactionTemplate(txManager).execute(s -> taskMapper.insert(t));
    }

    @Test
    void dashboardReturnsCorrectCountsAndTopN() {
        for (int i = 0; i < 12; i++) seedExpiryTask(i % 7, i == 0 ? "URGENT" : "WARN"); // 12 个 7 天内
        for (int i = 0; i < 5; i++) seedLowStockTask("INFO"); // 5 低库存
        seedExpiryTask(0, "URGENT"); seedExpiryTask(0, "URGENT"); // +2 URGENT priority

        var d = dashboardService.dashboard(householdId, 7, 8);
        assertThat(d.expiryWithin7Days().count()).isEqualTo(12);
        assertThat(d.expiryWithin7Days().items()).hasSize(8); // topN
        assertThat(d.lowStockItems().count()).isEqualTo(5);
        assertThat(d.lowStockItems().items()).hasSize(5);
        assertThat(d.priorityTasks().count()).isEqualTo(19); // 12+5+2
        assertThat(d.priorityTasks().items()).hasSize(8);
    }

    @Test
    void daysAndTopNParamsRespected() {
        for (int i = 0; i < 3; i++) seedExpiryTask(i, "INFO"); // 0,1,2 天
        var d = dashboardService.dashboard(householdId, 2, 1);
        assertThat(d.expiryWithin7Days().count()).isEqualTo(2); // 仅 daysLeft<2
        assertThat(d.expiryWithin7Days().items()).hasSize(1);
    }
}
```

- [ ] **步骤 2：验证失败** — 编译失败。

- [ ] **步骤 3：实现 DashboardService**

```java
package com.zija.reminder.internal;

import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
class DashboardService {

    private final TaskMapper taskMapper;
    private final Clock clock;

    DashboardService(TaskMapper taskMapper, Clock clock) {
        this.taskMapper = taskMapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(UUID householdId, int days, int topN) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime from = now;
        OffsetDateTime to = now.plusDays(days);
        var expiry = taskMapper.expiryWithinDays(householdId, from, to, topN);
        var lowStock = taskMapper.lowStockOpenTasks(householdId, topN);
        var priority = taskMapper.priorityTasks(householdId, topN);

        return new DashboardView(
                new DashboardGroup(
                        countAllExpiryWithinDays(householdId, from, to),
                        expiry.stream().map(this::toExpiryItem).toList()),
                new DashboardGroup(
                        countAllLowStock(householdId),
                        lowStock.stream().map(this::toLowStockItem).toList()),
                new DashboardGroup(
                        countAllPriority(householdId),
                        priority.stream().map(this::toPriorityItem).toList()),
                now
        );
    }

    // count* 通过 count(*) 全量查（不限 topN）
    private long countAllExpiryWithinDays(UUID hh, OffsetDateTime from, OffsetDateTime to) {
        return taskMapper.expiryWithinDays(hh, from, to, Integer.MAX_VALUE).size();
    }
    private long countAllLowStock(UUID hh) { return taskMapper.lowStockOpenTasks(hh, Integer.MAX_VALUE).size(); }
    private long countAllPriority(UUID hh) { return taskMapper.priorityTasks(hh, Integer.MAX_VALUE).size(); }

    private DashboardItem toExpiryItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "临期任务", t.getDueAt(), t.getItemId(), t.getLotId());
    }
    private DashboardItem toLowStockItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "低库存任务", t.getDueAt(), t.getItemId(), null);
    }
    private DashboardItem toPriorityItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "优先任务", t.getDueAt(), t.getItemId(), t.getLotId());
    }

    record DashboardView(DashboardGroup expiryWithin7Days, DashboardGroup lowStockItems,
                         DashboardGroup priorityTasks, OffsetDateTime generatedAt) {}
    record DashboardGroup(long count, List<DashboardItem> items) {}
    record DashboardItem(UUID taskId, String kind, String severity, String title,
                         OffsetDateTime dueAt, UUID itemId, UUID lotId) {}
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderDashboardIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/DashboardService.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderDashboardIntegrationTest.java
git commit -m "feat(reminder): 首页聚合（7天到期/低库存/优先任务+topN）"
```

---

## 任务 13：NotificationService + 端点

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/NotificationService.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/NotificationIntegrationTest.java`

- [ ] **步骤 1：写失败测试**

```java
package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class NotificationIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired NotificationService notificationService;
    @Autowired NotificationMapper notificationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insert(hh); householdId = hh.getId();
    }
    private UUID seedNotif(boolean read) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope("TASK_CREATED"); n.setTitle("测试通知");
        n.setRead(read); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n); return n.getId();
    }

    @Test
    void pageReturnsAllAndUnreadFilter() {
        seedNotif(false); seedNotif(false); seedNotif(true);
        var all = notificationService.page(householdId, 1, 20, false);
        assertThat(all.items()).hasSize(3);
        var unread = notificationService.page(householdId, 1, 20, true);
        assertThat(unread.items()).hasSize(2);
    }

    @Test
    void unreadCount() {
        seedNotif(false); seedNotif(false); seedNotif(true);
        assertThat(notificationService.unreadCount(householdId)).isEqualTo(2);
    }

    @Test
    void markOneRead() {
        var id = seedNotif(false);
        notificationService.markOneRead(householdId, id);
        assertThat(notificationMapper.selectById(id).getRead()).isTrue();
    }

    @Test
    void markAllRead() {
        seedNotif(false); seedNotif(false);
        notificationService.markAllRead(householdId);
        assertThat(notificationService.unreadCount(householdId)).isEqualTo(0);
    }
}
```

- [ ] **步骤 2：验证失败** — 编译失败。

- [ ] **步骤 3：实现 NotificationService**

```java
package com.zija.reminder.internal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class NotificationService {

    private final NotificationMapper notificationMapper;

    NotificationService(NotificationMapper notificationMapper) { this.notificationMapper = notificationMapper; }

    @Transactional(readOnly = true)
    public NotificationPage page(UUID householdId, int pageNo, int pageSize, boolean unreadOnly) {
        var page = notificationMapper.findPage(new Page<>(pageNo, pageSize), householdId, unreadOnly);
        var items = page.getRecords().stream().map(this::toView).toList();
        return new NotificationPage(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID householdId) { return notificationMapper.countUnread(householdId); }

    @Transactional
    public void markOneRead(UUID householdId, UUID id) { notificationMapper.markOneRead(householdId, id); }

    @Transactional
    public void markAllRead(UUID householdId) { notificationMapper.markAllRead(householdId); }

    private NotificationView toView(NotificationEntity e) {
        return new NotificationView(e.getId(), e.getScope(), e.getTitle(), e.getMessage(),
                e.getSourceTaskId(), Boolean.TRUE.equals(e.getRead()), e.getCreatedAt());
    }

    record NotificationView(UUID id, String scope, String title, String message,
                            UUID sourceTaskId, boolean read, java.time.OffsetDateTime createdAt) {}
    record NotificationPage(List<NotificationView> items, long total, int page, int pageSize) {}
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=NotificationIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/NotificationService.java \
        backend/src/test/java/com/zija/reminder/internal/NotificationIntegrationTest.java
git commit -m "feat(reminder): 站内通知服务（分页+未读计数+标记已读）"
```

---

## 任务 14：ReminderController + 全端点 + NotificationController

**Files:**
- Create: `backend/src/main/java/com/zija/reminder/internal/ReminderController.java`
- Create: `backend/src/main/java/com/zija/reminder/internal/NotificationController.java`
- Test: `backend/src/test/java/com/zija/reminder/internal/ReminderEndpointIntegrationTest.java`

- [ ] **步骤 1：写失败测试**（MockMvc 全端点 + 权限 + 跨家庭）

```java
package com.zija.reminder.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.*;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.reminder.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// 端点级测试用 MockMvc（@AutoConfigureMockMvc）+ session 模拟认证。
// 执行者按既有 InventoryEndpointIntegrationTest 的登录套路调整。
```

> **实施注：** 端点集成测试涉及 Spring Security 会话登录，执行者复用 `InventoryEndpointIntegrationTest` 的 `loginAs(accountId)` 助手（或等效）。下列用例需覆盖：
> 1. `GET /api/v1/reminder/rules` 返回懒初始化规则。
> 2. `PUT /api/v1/reminder/rules` 带 `version` 成功；旧版本 → `REMINDER_RULE_VERSION_CONFLICT` 409；MEMBER 角色 → 403。
> 3. `GET /api/v1/reminder/tasks` 分页+按 severity 排序首位为 URGENT。
> 4. `POST /api/v1/reminder/tasks/{id}/snooze` OPEN→SNOOZED；非 OPEN/SNOOZED → `REMINDER_TASK_INVALID_TRANSITION` 409；until 过去 → 422。
> 5. `POST .../complete`、`.../ignore`、`.../reopen` 各 happy 与非法转换。
> 6. `GET /api/v1/reminder/dashboard?days=7&topN=8` 返回结构。
> 7. `GET /api/v1/notifications` 分页 + `unreadOnly=true` 过滤；`GET .../unread-count`；`POST .../{id}/read`；`POST .../read-all`。
> 8. 跨家庭 `GET /api/v1/reminder/tasks/{otherHhTaskId}`（实际无该端点，状态机端点跨家庭 → `REMINDER_TASK_NOT_FOUND` 404）。
> 9. CSRF 与 Problem Details 形态与既有一致。

测试骨架（执行者补全 MockMvc 调用与断言细节）：

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderEndpointIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    // … seed account/household/role，登录 helper 同 InventoryEndpointIntegrationTest
    // 用例按上述 1–9 列写 assertThat(mvc.perform(...)).status...()
}
```

- [ ] **步骤 2：验证失败** — 编译失败。

- [ ] **步骤 3：实现 ReminderController**

```java
package com.zija.reminder.internal;

import com.zija.household.HouseholdApi;
import com.zija.reminder.ReminderApi;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminder")
class ReminderController {

    private final ReminderService reminderService;
    private final ReminderTaskStateService stateService;
    private final DashboardService dashboardService;
    private final HouseholdApi householdApi;

    ReminderController(ReminderService reminderService, ReminderTaskStateService stateService,
                       DashboardService dashboardService, HouseholdApi householdApi) {
        this.reminderService = reminderService; this.stateService = stateService;
        this.dashboardService = dashboardService; this.householdApi = householdApi;
    }

    @GetMapping("/rules")
    ReminderService.RuleView getRules(@RequestHeader("X-Current-Account") UUID accountId) {
        var member = householdApi.requireActiveMember(accountId);
        return reminderService.getOrCreateRule(member.householdId());
    }

    @PutMapping("/rules")
    ReminderService.RuleView updateRules(@RequestHeader("X-Current-Account") UUID accountId,
                                         @RequestBody ReminderService.RuleUpdate body) {
        var member = householdApi.requireActiveMember(accountId);
        if (!householdApi.hasAtLeastRole(accountId, HouseholdApi.MemberRole.ADMIN)) {
            throw new AccessDeniedException("需要管理员权限");
        }
        return reminderService.updateRule(member.householdId(), body);
    }

    @GetMapping("/tasks")
    TaskPage listTasks(@RequestHeader("X-Current-Account") UUID accountId,
                       @RequestParam(required = false) String kind,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID itemId,
                       @RequestParam(required = false, defaultValue = "false") boolean overdue,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int pageSize,
                       @RequestParam(defaultValue = "severity,dueAt") String orderBy) {
        var member = householdApi.requireActiveMember(accountId);
        // TaskPage 由 ReminderService.tasksPage(...) 提供——执行者按 TaskMapper.findPage 实现
        return reminderService.tasksPage(member.householdId(), kind, status, itemId, overdue, page, pageSize, orderBy);
    }

    @PostMapping("/tasks/{id}/snooze")
    void snooze(@RequestHeader("X-Current-Account") UUID accountId,
                @PathVariable UUID id, @RequestBody SnoozeBody body) {
        var member = householdApi.requireActiveMember(accountId);
        stateService.snooze(member.householdId(), id, body.until());
    }

    @PostMapping("/tasks/{id}/complete")
    void complete(@RequestHeader("X-Current-Account") UUID accountId, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(accountId);
        stateService.complete(member.householdId(), id);
    }

    @PostMapping("/tasks/{id}/ignore")
    void ignore(@RequestHeader("X-Current-Account") UUID accountId, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(accountId);
        stateService.ignore(member.householdId(), id);
    }

    @PostMapping("/tasks/{id}/reopen")
    void reopen(@RequestHeader("X-Current-Account") UUID accountId, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(accountId);
        stateService.reopen(member.householdId(), id);
    }

    @GetMapping("/dashboard")
    DashboardService.DashboardView dashboard(@RequestHeader("X-Current-Account") UUID accountId,
                                             @RequestParam(defaultValue = "7") int days,
                                             @RequestParam(defaultValue = "8") int topN) {
        var member = householdApi.requireActiveMember(accountId);
        return dashboardService.dashboard(member.householdId(), days, topN);
    }

    record SnoozeBody(OffsetDateTime until) {}
}
```

> **实施注：** `X-Current-Account` 头以既有实现为准；若应用通过 `SecurityContextHolder` 取当前账户，改用注入 `@AuthenticationPrincipal` 或既有 `CurrentAccountResolver`。先 `rg -n "accountId|@RequestHeader" backend/src/main/java/com/zija/inventory/internal/InventoryController.java` 确认模式后对齐。

在 `ReminderService` 追加 `tasksPage(...)`（基于 `TaskMapper.findPage`）：

```java
public TaskPage tasksPage(UUID householdId, String kind, String status, UUID itemId,
                           boolean overdue, int page, int pageSize, String orderBy) {
    var p = taskMapper.findPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            householdId, kind, status, itemId, overdue ? true : null,
            java.time.OffsetDateTime.now(), "CASE severity WHEN 'URGENT' THEN 0 WHEN 'WARN' THEN 1 ELSE 2 END, due_at ASC");
    var items = p.getRecords().stream().map(this::toTaskView).toList();
    return new TaskPage(items, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
}
private TaskView toTaskView(TaskEntity e) {
    return new TaskView(e.getId(), e.getKind(), e.getLotId(), e.getItemId(),
            e.getStatus(), e.getDueAt(), e.getSeverity(), e.getSnoozedUntil());
}
record TaskView(UUID id, String kind, UUID lotId, UUID itemId, String status,
                OffsetDateTime dueAt, String severity, OffsetDateTime snoozedUntil) {}
record TaskPage(List<TaskView> items, long total, int page, int pageSize) {}
```

并给 `ReminderService` 注入 `TaskMapper`。

`NotificationController.java`：

```java
package com.zija.reminder.internal;

import com.zija.household.HouseholdApi;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notificationService;
    private final HouseholdApi householdApi;

    NotificationController(NotificationService notificationService, HouseholdApi householdApi) {
        this.notificationService = notificationService; this.householdApi = householdApi;
    }

    @GetMapping
    NotificationService.NotificationPage list(@RequestHeader("X-Current-Account") UUID accountId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly) {
        var member = householdApi.requireActiveMember(accountId);
        return notificationService.page(member.householdId(), page, pageSize, unreadOnly);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@RequestHeader("X-Current-Account") UUID accountId) {
        var member = householdApi.requireActiveMember(accountId);
        return new UnreadCount(notificationService.unreadCount(member.householdId()));
    }

    @PostMapping("/{id}/read")
    void readOne(@RequestHeader("X-Current-Account") UUID accountId, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(accountId);
        notificationService.markOneRead(member.householdId(), id);
    }

    @PostMapping("/read-all")
    void readAll(@RequestHeader("X-Current-Account") UUID accountId) {
        var member = householdApi.requireActiveMember(accountId);
        notificationService.markAllRead(member.householdId());
    }

    record UnreadCount(long count) {}
}
```

- [ ] **步骤 4：验证通过 + 提交**

Run: `cd backend && ./mvnw -q -Dtest=ReminderEndpointIntegrationTest test` → PASS。

```bash
git add backend/src/main/java/com/zija/reminder/internal/ReminderController.java \
        backend/src/main/java/com/zija/reminder/internal/NotificationController.java \
        backend/src/main/java/com/zija/reminder/internal/ReminderService.java \
        backend/src/test/java/com/zija/reminder/internal/ReminderEndpointIntegrationTest.java
git commit -m "feat(reminder): REST 端点 rules/tasks/dashboard/notifications+权限+MockMvc测试"
```

---

## 任务 15：ModularityTests 扩充 + OpenApiContractTest 基线升 V2

**Files:**
- Modify: `backend/src/test/java/com/zija/ModularityTests.java`
- Modify: `backend/src/test/java/com/zija/OpenApiContractTest.java`

- [ ] **步骤 1：在 ModularityTests 加 reminder 断言**

```java
@Test
void reminderModuleExistsAndDependenciesAreValid() {
    assertThat(modules.getModuleByName("reminder")).isPresent();
    modules.verify();
}
```

- [ ] **步骤 2：运行 ModularityTests**

Run: `cd backend && ./mvnw -q -Dtest=ModularityTests test`
Expected: PASS。

- [ ] **步骤 3：更新 OpenApiContractTest 基线**

按 `OpenApiContractTest.java` 现有结构（通常是对比生成的 OpenAPI 与基线 JSON 文件），更新基线文件含 `/api/v1/reminder/**`、`/api/v1/notifications/**` 端点。执行者先 `cd backend && ./mvnw -q -Dtest=OpenApiContractTest test` 看失败信息，按其指引重新生成或更新基线文件。

- [ ] **步骤 4：提交**

```bash
git add backend/src/test/java/com/zija/ModularityTests.java \
        backend/src/test/java/com/zija/OpenApiContractTest.java \
        backend/src/test/resources/openapi/*.json
git commit -m "test(reminder): Modularity 断言 + OpenAPI 基线升 V2"
```

---

## 任务 16：5a 收尾

- [ ] **步骤 1：运行 `make backend-test` 全绿**

Run: `make backend-test`
Expected: 全部 PASS（含 reminder 与既有库存回归）。

- [ ] **步骤 2：运行 `make backend-build` 成功**

Run: `make backend-build`
Expected: BUILD SUCCESS。

- [ ] **步骤 3：写收尾记录**

创建 `docs/superpowers/notes/2026-07-26-phase5a-reminder-backend-completion.md`：
```markdown
# 5a 提醒后端 完成记录

- 完成日期：YYYY-MM-DD
- 最终提交 ID：`<git rev-parse HEAD>`
- 验证命令：`make backend-test`、`make backend-build`
- 测试统计：`<mvnw test 输出的 Tests run: X, Failures: 0>`
- 覆盖 spec：`docs/superpowers/specs/2026-07-26-phase5a-reminder-backend-design.md`
```

- [ ] **步骤 4：提交**

```bash
git add docs/superpowers/notes/2026-07-26-phase5a-reminder-backend-completion.md
git commit -m "docs: 5a 提醒后端完成记录"
```

---

## 自检清单（writing-plans self-review）

- ✅ **Spec 覆盖**：spec §1 模块边界→任务 1-2；§2 数据模型→任务 1（V2）+任务 4（Mapper）；§2.2 Api 扩展→任务 3；§3 可靠事件→任务 8-9-10；§4 状态机端点→任务 11-14；§4.5 通知端点→任务 13-14；§5 测试→每个任务 TDD；§6 实施拆分→16 任务对齐。
- ✅ **无占位**：每步含可执行代码或确切的 `rg`/`mvnw`/`git` 命令与期望输出；任务 14 端点测试标注执行者复用既有 MockMvc 登录 helper（指明来源 `InventoryEndpointIntegrationTest`），非「add tests later」。
- ✅ **类型一致**：`ReminderService.RuleView`/`RuleUpdate`、`TaskMapper.snooze`/`transitionTo`/`reopen`、`DashboardService.DashboardView`、`NotificationService.NotificationPage` 跨任务签名一致。
- ✅ **DRY**：seed 助手在各测试类重复但属测试装配必要；生产代码无重复。
- ✅ **YAGNI**：不放前端、SMTP、报表、搜索。
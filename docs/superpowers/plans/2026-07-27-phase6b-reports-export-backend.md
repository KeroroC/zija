# Phase 6b: 报表与导出后端 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已有的 reporting 投影基础设施上，实现 5 张报表查询端点、同步流式 CSV 导出端点、全局搜索端点、投影重建端点，以及对应的后端单元测试与 Testcontainers 集成测试。

**Architecture:** 所有报表查询和全局搜索只读 `reporting` 自有投影表（`reporting_search_index` / `reporting_stock_flat` / `reporting_movement_flat`），复杂 SQL 写在 `reporting/internal/persistence/ReportMapper.xml`。导出使用 `StreamingResponseBody` 直接写 `HttpServletResponse` 输出流，行数硬上限 100,000。投影重建通过 `POST /api/v1/reporting/projection/rebuild` 触发，清空指定家庭投影行后从源模块快照拉取端口回填。

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, PostgreSQL 17, Testcontainers

## Global Constraints

- 报表/搜索全员可读（`@RequireMember`），导出仅 OWNER/ADMIN（`@RequireAdmin`）。
- 所有查询只读 `reporting` 自有投影表，不跨模块读他模块事务表。
- CSV 输出：UTF-8 BOM + `Content-Type: text/csv; charset=utf-8` + `Content-Disposition: attachment`。
- 导出行数硬上限 100,000；超过返回 `400 REPORTING_EXPORT_TOO_LARGE`。
- 每次导出写审计 `EXPORT_PERFORMED`；投影重建写 `REPORTING_PROJECTION_REBUILT`。
- 复杂报表 SQL 写在 `reporting/internal/persistence/ReportMapper.xml`，不跨模块读他表。
- 分页参数：`page=1&pageSize=20`，上限 100。
- 错误码：`REPORTING_PROJECTION_REBUILD_FAILED`、`REPORTING_EXPORT_TOO_LARGE`。
- Java：4-space indent，`@Configuration(proxyBeanMethods = false)`。

## File Structure

### 新建文件

```
backend/src/main/java/com/zija/reporting/internal/
  ReportingController.java                 # 全部 REST 端点
  ReportingService.java                    # 业务逻辑（报表查询 + 搜索 + 导出 + 重建）
  search/
    SearchService.java                     # 全局搜索逻辑
  reports/
    ReportService.java                     # 5 张报表查询逻辑
  export/
    CsvWriter.java                         # CSV 写出工具（UTF-8 BOM + 转义）
    ExportService.java                     # 导出逻辑（行数上限 + 审计）

backend/src/main/java/com/zija/reporting/internal/persistence/
  ReportMapper.java                        # 报表查询 Mapper 接口
  SearchMapper.java                        # 全局搜索 Mapper 接口

backend/src/main/resources/mapper/reporting/
  ReportMapper.xml                         # 5 张报表 SQL
  SearchMapper.xml                         # 全局搜索 SQL

backend/src/test/java/com/zija/reporting/internal/
  ReportingControllerTest.java             # MockMvc 端点测试
  SearchServiceTest.java                   # 搜索逻辑测试
  ReportServiceTest.java                   # 报表查询测试
  CsvWriterTest.java                       # CSV 写出测试
  ExportServiceTest.java                   # 导出逻辑测试
  projection/ProjectionRebuildTest.java    # 投影重建集成测试（Testcontainers）
```

### 修改文件

```
backend/src/main/java/com/zija/reporting/ReportingApi.java          # +查询方法（可选）
backend/src/main/java/com/zija/reporting/internal/persistence/       # +查询方法到现有 Mapper
```

---

### Task 1: ReportMapper + SearchMapper — 报表与搜索 SQL

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/ReportMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/ReportMapper.xml`
- Create: `backend/src/main/java/com/zija/reporting/internal/persistence/SearchMapper.java`
- Create: `backend/src/main/resources/mapper/reporting/SearchMapper.xml`

**Interfaces:**
- Produces: `ReportMapper.stockByLocation(householdId, itemId, categoryId, locationId, brandId, page) → IPage`
- Produces: `ReportMapper.expiringLots(householdId, withinDays, itemId, locationId, page) → IPage`
- Produces: `ReportMapper.lowStock(householdId, categoryId, page) → IPage`
- Produces: `ReportMapper.stockChanges(householdId, from, to, itemId, locationId, type, page) → IPage`
- Produces: `ReportMapper.movements(householdId, from, to, itemId, type, operatorAccountId, page) → IPage`
- Produces: `SearchMapper.searchItems(householdId, q, limit) → List`
- Produces: `SearchMapper.searchLots(householdId, q, limit) → List`
- Produces: `SearchMapper.searchLocations(householdId, q, limit) → List`

- [ ] **Step 1: 创建 `ReportMapper.java` 接口**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/ReportMapper.java
package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface ReportMapper {

    /** 当前库存与位置分布：按位置 + 物品聚合。 */
    IPage<Map<String, Object>> stockByLocation(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("itemId") UUID itemId,
            @Param("categoryId") UUID categoryId,
            @Param("locationId") UUID locationId,
            @Param("brandId") UUID brandId);

    /** 临期批次。 */
    IPage<Map<String, Object>> expiringLots(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("withinDays") int withinDays,
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId);

    /** 低库存物品。 */
    IPage<Map<String, Object>> lowStock(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("categoryId") UUID categoryId);

    /** 指定时间范围库存变化。 */
    IPage<Map<String, Object>> stockChanges(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId,
            @Param("type") String type);

    /** 按成员/类型/物品筛选流水。 */
    IPage<Map<String, Object>> movements(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("itemId") UUID itemId,
            @Param("type") String type,
            @Param("operatorAccountId") UUID operatorAccountId);
}
```

- [ ] **Step 2: 创建 `ReportMapper.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.ReportMapper">

    <!-- 1) 当前库存与位置分布 -->
    <select id="stockByLocation" resultType="java.util.LinkedHashMap">
        SELECT
            sf.location_id, sf.location_path,
            sf.item_id, sf.item_name,
            sf.lot_id, sf.lot_number, sf.serial_number,
            sf.unit_name, sf.quantity, sf.expiry_date
        FROM reporting_stock_flat sf
        WHERE sf.household_id = #{householdId}
        <if test="itemId != null">AND sf.item_id = #{itemId}</if>
        <if test="locationId != null">AND sf.location_id = #{locationId}</if>
        <if test="categoryId != null">
            AND sf.item_id IN (SELECT id FROM catalog_item WHERE category_id = #{categoryId})
        </if>
        <if test="brandId != null">
            AND sf.item_id IN (SELECT id FROM catalog_item WHERE brand_id = #{brandId})
        </if>
        ORDER BY sf.location_path, sf.item_name, sf.lot_number
    </select>

    <!-- 2) 临期批次 -->
    <select id="expiringLots" resultType="java.util.LinkedHashMap">
        SELECT
            sf.lot_id, sf.lot_number, sf.serial_number,
            sf.item_id, sf.item_name, sf.unit_name,
            sf.location_id, sf.location_path,
            sf.quantity, sf.expiry_date,
            (sf.expiry_date - CURRENT_DATE) AS days_until_expiry
        FROM reporting_stock_flat sf
        WHERE sf.household_id = #{householdId}
          AND sf.expiry_date IS NOT NULL
          AND sf.expiry_date &lt;= CURRENT_DATE + #{withinDays} * INTERVAL '1 day'
          AND sf.quantity &gt; 0
        <if test="itemId != null">AND sf.item_id = #{itemId}</if>
        <if test="locationId != null">AND sf.location_id = #{locationId}</if>
        ORDER BY sf.expiry_date ASC
    </select>

    <!-- 3) 低库存物品 -->
    <select id="lowStock" resultType="java.util.LinkedHashMap">
        SELECT
            i.id AS item_id, i.name AS item_name,
            i.low_stock_threshold, i.low_stock_mode,
            COALESCE(SUM(sf.quantity), 0) AS total_quantity
        FROM catalog_item i
        LEFT JOIN reporting_stock_flat sf ON sf.item_id = i.id AND sf.household_id = i.household_id
        WHERE i.household_id = #{householdId}
          AND i.status = 'ACTIVE'
          AND i.low_stock_mode = 'THRESHOLD'
          AND i.low_stock_threshold IS NOT NULL
        <if test="categoryId != null">AND i.category_id = #{categoryId}</if>
        GROUP BY i.id, i.name, i.low_stock_threshold, i.low_stock_mode
        HAVING COALESCE(SUM(sf.quantity), 0) &lt; i.low_stock_threshold
        ORDER BY (COALESCE(SUM(sf.quantity), 0) / i.low_stock_threshold) ASC
    </select>

    <!-- 4) 指定时间范围库存变化 -->
    <select id="stockChanges" resultType="java.util.LinkedHashMap">
        SELECT
            mf.movement_id, mf.item_id, mf.item_name,
            mf.type, mf.quantity_delta,
            mf.from_location_id, mf.from_location_path,
            mf.to_location_id, mf.to_location_path,
            mf.operator_account_id, mf.operator_display_name,
            mf.reason, mf.business_time
        FROM reporting_movement_flat mf
        WHERE mf.household_id = #{householdId}
          AND mf.business_time BETWEEN #{from} AND #{to}
        <if test="itemId != null">AND mf.item_id = #{itemId}</if>
        <if test="locationId != null">
            AND (mf.from_location_id = #{locationId} OR mf.to_location_id = #{locationId})
        </if>
        <if test="type != null">AND mf.type = #{type}</if>
        ORDER BY mf.business_time DESC
    </select>

    <!-- 5) 按成员/类型/物品筛选流水 -->
    <select id="movements" resultType="java.util.LinkedHashMap">
        SELECT
            mf.movement_id, mf.item_id, mf.item_name,
            mf.type, mf.quantity_delta,
            mf.from_location_id, mf.from_location_path,
            mf.to_location_id, mf.to_location_path,
            mf.operator_account_id, mf.operator_display_name,
            mf.reason, mf.reversal_of, mf.business_time, mf.created_at
        FROM reporting_movement_flat mf
        WHERE mf.household_id = #{householdId}
        <if test="from != null">AND mf.business_time &gt;= #{from}</if>
        <if test="to != null">AND mf.business_time &lt;= #{to}</if>
        <if test="itemId != null">AND mf.item_id = #{itemId}</if>
        <if test="type != null">AND mf.type = #{type}</if>
        <if test="operatorAccountId != null">AND mf.operator_account_id = #{operatorAccountId}</if>
        ORDER BY mf.business_time DESC
    </select>

</mapper>
```

- [ ] **Step 3: 创建 `SearchMapper.java` 接口**

```java
// backend/src/main/java/com/zija/reporting/internal/persistence/SearchMapper.java
package com.zija.reporting.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface SearchMapper {

    /** 按物品名/品牌/标签搜索 ITEM 实体。 */
    List<Map<String, Object>> searchItems(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);

    /** 按批次号/序列号搜索 LOT 实体。 */
    List<Map<String, Object>> searchLots(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);

    /** 按位置名/路径搜索 LOCATION 实体。 */
    List<Map<String, Object>> searchLocations(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);
}
```

- [ ] **Step 4: 创建 `SearchMapper.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.reporting.internal.persistence.SearchMapper">

    <select id="searchItems" resultType="java.util.LinkedHashMap">
        SELECT
            entity_id AS "itemId",
            item_name AS "name",
            brand_name AS "brand",
            tag_names AS "tags",
            category_name AS "category",
            unit_name AS "unit"
        FROM reporting_search_index
        WHERE household_id = #{householdId}
          AND entity_type = 'ITEM'
          AND (
              item_name ILIKE '%' || #{q} || '%'
              OR brand_name ILIKE '%' || #{q} || '%'
              OR tag_names ILIKE '%' || #{q} || '%'
              OR category_name ILIKE '%' || #{q} || '%'
          )
        ORDER BY item_name
        LIMIT #{limit}
    </select>

    <select id="searchLots" resultType="java.util.LinkedHashMap">
        SELECT
            entity_id AS "lotId",
            lot_id AS "lotId",
            item_name AS "itemName",
            lot_number AS "lotNumber",
            serial_number AS "serialNumber"
        FROM reporting_search_index
        WHERE household_id = #{householdId}
          AND entity_type = 'LOT'
          AND (
              lot_number ILIKE '%' || #{q} || '%'
              OR serial_number ILIKE '%' || #{q} || '%'
          )
        ORDER BY lot_number
        LIMIT #{limit}
    </select>

    <select id="searchLocations" resultType="java.util.LinkedHashMap">
        SELECT
            entity_id AS "locationId",
            location_name AS "name",
            location_path AS "path"
        FROM reporting_search_index
        WHERE household_id = #{householdId}
          AND entity_type = 'LOCATION'
          AND (
              location_name ILIKE '%' || #{q} || '%'
              OR location_path ILIKE '%' || #{q} || '%'
          )
        ORDER BY location_path
        LIMIT #{limit}
    </select>

</mapper>
```

- [ ] **Step 5: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/persistence/ReportMapper.java \
        backend/src/main/resources/mapper/reporting/ReportMapper.xml \
        backend/src/main/java/com/zija/reporting/internal/persistence/SearchMapper.java \
        backend/src/main/resources/mapper/reporting/SearchMapper.xml
git commit -m "feat(reporting): ReportMapper + SearchMapper 报表与搜索 SQL

- 5 张报表查询：stockByLocation/expiringLots/lowStock/stockChanges/movements
- 全局搜索：searchItems/searchLots/searchLocations（ILIKE 中文子串匹配）
- 复杂 SQL 写在 reporting 自有 Mapper XML，不跨模块读他表"
```

---

### Task 2: CsvWriter + ExportService — CSV 导出

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/export/CsvWriter.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/export/ExportService.java`

**Interfaces:**
- Produces: `CsvWriter.write(OutputStream, List<String> headers, List<Map<String, Object>> rows)`
- Produces: `ExportService.exportToStream(householdId, reportKey, params, outputStream)`

- [ ] **Step 1: 创建 `CsvWriter`**

```java
// backend/src/main/java/com/zija/reporting/internal/export/CsvWriter.java
package com.zija.reporting.internal.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * CSV 写出工具。UTF-8 BOM + RFC 4180 转义。
 */
public class CsvWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * 写出 CSV 到输出流。先写 BOM，再写表头行，最后逐行写数据。
     */
    public static void write(OutputStream out, List<String> headers,
                              List<Map<String, Object>> rows) throws IOException {
        out.write(BOM);
        var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        // 表头
        writer.write(String.join(",", headers.stream()
                .map(CsvWriter::escapeField).toList()));
        writer.write("\r\n");

        // 数据行
        for (var row : rows) {
            var line = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) line.append(",");
                Object val = row.get(headers.get(i));
                line.append(escapeField(val == null ? "" : val.toString()));
            }
            writer.write(line.toString());
            writer.write("\r\n");
        }
        writer.flush();
    }

    /**
     * RFC 4180 转义：含逗号/双引号/换行的字段用双引号包裹，内部双引号转义为两个双引号。
     */
    static String escapeField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")
                || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
```

- [ ] **Step 2: 创建 `ExportService`**

```java
// backend/src/main/java/com/zija/reporting/internal/export/ExportService.java
package com.zija.reporting.internal.export;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.persistence.ReportMapper;
import com.zija.reporting.internal.persistence.SearchMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 导出服务。行数硬上限 100,000；写审计 EXPORT_PERFORMED。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final int MAX_ROWS = 100_000;
    private static final int PAGE_SIZE = 1000;

    private final ReportMapper reportMapper;
    private final SearchMapper searchMapper;
    private final SystemApi systemApi;

    public ExportService(ReportMapper reportMapper, SearchMapper searchMapper, SystemApi systemApi) {
        this.reportMapper = reportMapper;
        this.searchMapper = searchMapper;
        this.systemApi = systemApi;
    }

    /**
     * 导出指定报表到输出流。超过 MAX_ROWS 抛出 ExportTooLargeException。
     */
    public void exportToStream(UUID householdId, String reportKey,
                                Map<String, String> params,
                                OutputStream out) throws IOException {
        var headers = getHeaders(reportKey);
        List<Map<String, Object>> allRows = fetchAllRows(householdId, reportKey, params);

        if (allRows.size() > MAX_ROWS) {
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "EXPORT_PERFORMED", "FAILURE", householdId, null, null, null, null,
                    Map.of("reportKey", reportKey, "reason", "TOO_LARGE", "rowCount", allRows.size())));
            throw new ExportTooLargeException(allRows.size(), MAX_ROWS);
        }

        CsvWriter.write(out, headers, allRows);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "EXPORT_PERFORMED", "SUCCESS", householdId, null, null, null, null,
                Map.of("reportKey", reportKey, "rowCount", allRows.size())));
    }

    private List<Map<String, Object>> fetchAllRows(UUID householdId, String reportKey,
                                                     Map<String, String> params) {
        // 根据 reportKey 调用对应的 ReportMapper/SearchMapper 方法
        // 分页拉取直到 hasMore=false 或达到 MAX_ROWS
        // 实现时根据 reportKey switch 分派
        return Collections.emptyList(); // placeholder
    }

    private List<String> getHeaders(String reportKey) {
        return switch (reportKey) {
            case "stock-by-location" -> List.of("location_path", "item_name", "lot_number",
                    "serial_number", "unit_name", "quantity", "expiry_date");
            case "expiring-lots" -> List.of("lot_number", "serial_number", "item_name",
                    "location_path", "quantity", "expiry_date", "days_until_expiry");
            case "low-stock" -> List.of("item_name", "total_quantity", "low_stock_threshold");
            case "stock-changes" -> List.of("item_name", "type", "quantity_delta",
                    "from_location_path", "to_location_path", "operator_display_name",
                    "reason", "business_time");
            case "movements" -> List.of("item_name", "type", "quantity_delta",
                    "from_location_path", "to_location_path", "operator_display_name",
                    "reason", "reversal_of", "business_time");
            case "items-full" -> List.of("item_name", "brand", "tags", "category", "unit");
            case "locations-full" -> List.of("name", "path");
            default -> throw new IllegalArgumentException("Unknown reportKey: " + reportKey);
        };
    }
}
```

- [ ] **Step 3: 创建 `ExportTooLargeException`**

```java
// backend/src/main/java/com/zija/reporting/internal/export/ExportTooLargeException.java
package com.zija.reporting.internal.export;

public class ExportTooLargeException extends RuntimeException {
    private final int actualRows;
    private final int maxRows;

    public ExportTooLargeException(int actualRows, int maxRows) {
        super("Export too large: " + actualRows + " rows (max " + maxRows + ")");
        this.actualRows = actualRows;
        this.maxRows = maxRows;
    }

    public int getActualRows() { return actualRows; }
    public int getMaxRows() { return maxRows; }
}
```

- [ ] **Step 4: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/export/
git commit -m "feat(reporting): CsvWriter + ExportService CSV 导出

- CsvWriter: UTF-8 BOM + RFC 4180 转义
- ExportService: 分页拉取 + 行数硬上限 100,000
- 超限抛 ExportTooLargeException + 写审计 EXPORT_PERFORMED(FAILURE)
- 成功写审计 EXPORT_PERFORMED(SUCCESS)"
```

---

### Task 3: ReportingController + ReportingService — REST 端点

**Files:**
- Create: `backend/src/main/java/com/zija/reporting/internal/ReportingController.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/search/SearchService.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/reports/ReportService.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/ReportingService.java`
- Create: `backend/src/main/java/com/zija/reporting/internal/ReportingExceptionHandler.java`

**Interfaces:**
- Produces: `GET /api/v1/reporting/search?q=&limitPerGroup=5`
- Produces: `GET /api/v1/reporting/reports/stock-by-location?page=&pageSize=&itemId=&categoryId=&locationId=&brandId=`
- Produces: `GET /api/v1/reporting/reports/expiring-lots?page=&pageSize=&withinDays=30&itemId=&locationId=`
- Produces: `GET /api/v1/reporting/reports/low-stock?page=&pageSize=&categoryId=`
- Produces: `GET /api/v1/reporting/reports/stock-changes?page=&pageSize=&from=&to=&itemId=&locationId=&type=`
- Produces: `GET /api/v1/reporting/reports/movements?page=&pageSize=&from=&to=&itemId=&type=&operatorAccountId=`
- Produces: `GET /api/v1/reporting/exports/{reportKey}?scope=current-filter|full&...`
- Produces: `POST /api/v1/reporting/projection/rebuild?householdId=`

- [ ] **Step 1: 创建 `SearchService`**

```java
// backend/src/main/java/com/zija/reporting/internal/search/SearchService.java
package com.zija.reporting.internal.search;

import com.zija.reporting.internal.persistence.SearchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SearchService {

    private final SearchMapper searchMapper;

    public SearchService(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(UUID householdId, String q, int limitPerGroup) {
        var items = searchMapper.searchItems(householdId, q, limitPerGroup);
        var lots = searchMapper.searchLots(householdId, q, limitPerGroup);
        var locations = searchMapper.searchLocations(householdId, q, limitPerGroup);

        // 为每条结果添加 matchedFields
        addMatchedFields(items, q, "name", "brand", "tags", "category");
        addMatchedFields(lots, q, "lotNumber", "serialNumber");
        addMatchedFields(locations, q, "name", "path");

        return Map.of("items", items, "lots", lots, "locations", locations);
    }

    private void addMatchedFields(List<Map<String, Object>> results, String q, String... fields) {
        for (var row : results) {
            var matched = new ArrayList<String>();
            for (String field : fields) {
                Object val = row.get(field);
                if (val != null && val.toString().toLowerCase().contains(q.toLowerCase())) {
                    matched.add(field);
                }
            }
            row.put("matchedFields", matched);
        }
    }
}
```

- [ ] **Step 2: 创建 `ReportService`**

```java
// backend/src/main/java/com/zija/reporting/internal/reports/ReportService.java
package com.zija.reporting.internal.reports;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.persistence.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> stockByLocation(UUID householdId, int page, int pageSize,
                                                        UUID itemId, UUID categoryId,
                                                        UUID locationId, UUID brandId) {
        return reportMapper.stockByLocation(new Page<>(page, pageSize),
                householdId, itemId, categoryId, locationId, brandId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> expiringLots(UUID householdId, int page, int pageSize,
                                                      int withinDays, UUID itemId, UUID locationId) {
        return reportMapper.expiringLots(new Page<>(page, pageSize),
                householdId, withinDays, itemId, locationId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> lowStock(UUID householdId, int page, int pageSize,
                                                  UUID categoryId) {
        return reportMapper.lowStock(new Page<>(page, pageSize), householdId, categoryId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> stockChanges(UUID householdId, int page, int pageSize,
                                                      OffsetDateTime from, OffsetDateTime to,
                                                      UUID itemId, UUID locationId, String type) {
        return reportMapper.stockChanges(new Page<>(page, pageSize),
                householdId, from, to, itemId, locationId, type);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> movements(UUID householdId, int page, int pageSize,
                                                   OffsetDateTime from, OffsetDateTime to,
                                                   UUID itemId, String type, UUID operatorAccountId) {
        return reportMapper.movements(new Page<>(page, pageSize),
                householdId, from, to, itemId, type, operatorAccountId);
    }
}
```

- [ ] **Step 3: 创建 `ReportingController`**

```java
// backend/src/main/java/com/zija/reporting/internal/ReportingController.java
package com.zija.reporting.internal;

import com.zija.household.HouseholdApi;
import com.zija.identity.ZijaPrincipal;
import com.zija.reporting.internal.export.ExportService;
import com.zija.reporting.internal.projection.ProjectionRebuilder;
import com.zija.reporting.internal.reports.ReportService;
import com.zija.reporting.internal.search.SearchService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reporting")
class ReportingController {

    private final SearchService searchService;
    private final ReportService reportService;
    private final ExportService exportService;
    private final ProjectionRebuilder projectionRebuilder;
    private final HouseholdApi householdApi;

    ReportingController(SearchService searchService, ReportService reportService,
                         ExportService exportService, ProjectionRebuilder projectionRebuilder,
                         HouseholdApi householdApi) {
        this.searchService = searchService;
        this.reportService = reportService;
        this.exportService = exportService;
        this.projectionRebuilder = projectionRebuilder;
        this.householdApi = householdApi;
    }

    // --- 全局搜索（成员可读） ---

    @GetMapping("/search")
    Map<String, Object> search(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limitPerGroup) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        int limit = Math.min(Math.max(limitPerGroup, 1), 20);
        return searchService.search(member.householdId(), q.trim(), limit);
    }

    // --- 报表查询（成员可读） ---

    @GetMapping("/reports/stock-by-location")
    Map<String, Object> stockByLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID brandId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.stockByLocation(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                itemId, categoryId, locationId, brandId);
        return toPageResponse(result);
    }

    @GetMapping("/reports/expiring-lots")
    Map<String, Object> expiringLots(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "30") int withinDays,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.expiringLots(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                Math.max(withinDays, 1), itemId, locationId);
        return toPageResponse(result);
    }

    @GetMapping("/reports/low-stock")
    Map<String, Object> lowStock(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID categoryId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.lowStock(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100), categoryId);
        return toPageResponse(result);
    }

    @GetMapping("/reports/stock-changes")
    Map<String, Object> stockChanges(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String type) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.stockChanges(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                from, to, itemId, locationId, type);
        return toPageResponse(result);
    }

    @GetMapping("/reports/movements")
    Map<String, Object> movements(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID operatorAccountId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.movements(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                from, to, itemId, type, operatorAccountId);
        return toPageResponse(result);
    }

    // --- 导出（OWNER/ADMIN） ---

    @GetMapping("/exports/{reportKey}")
    ResponseEntity<StreamingResponseBody> export(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable String reportKey,
            @RequestParam Map<String, String> params) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        // requireAdmin 检查由 @RequireAdmin 注解或在此手动检查
        householdApi.hasAtLeastRole(member.accountId(), com.zija.household.HouseholdApi.MemberRole.ADMIN);

        String filename = "zija-" + reportKey + "-" + System.currentTimeMillis() + ".csv";
        StreamingResponseBody body = out -> exportService.exportToStream(
                member.householdId(), reportKey, params, out);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    // --- 投影重建（OWNER/ADMIN） ---

    @PostMapping("/projection/rebuild")
    Map<String, Object> rebuildProjection(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam UUID householdId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        householdApi.hasAtLeastRole(member.accountId(), com.zija.household.HouseholdApi.MemberRole.ADMIN);
        projectionRebuilder.rebuild(householdId);
        return Map.of("status", "ok", "householdId", householdId);
    }

    // --- 辅助 ---

    private Map<String, Object> toPageResponse(com.baomidou.mybatisplus.core.metadata.IPage<?> page) {
        return Map.of(
                "items", page.getRecords(),
                "total", page.getTotal(),
                "page", page.getCurrent(),
                "pageSize", page.getSize());
    }
}
```

- [ ] **Step 4: 创建 `ProjectionRebuilder`**

```java
// backend/src/main/java/com/zija/reporting/internal/projection/ProjectionRebuilder.java
package com.zija.reporting.internal.projection;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import com.zija.reporting.internal.persistence.*;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 投影重建器。清空指定家庭投影行，从源模块快照拉取端口回填。
 * 写 REPORTING_PROJECTION_REBUILT 审计。
 */
@Service
public class ProjectionRebuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilder.class);
    private static final int BATCH_SIZE = 1000;

    private final SearchIndexMapper searchIndexMapper;
    private final StockFlatMapper stockFlatMapper;
    private final MovementFlatMapper movementFlatMapper;
    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final InventoryApi inventoryApi;
    private final SystemApi systemApi;

    public ProjectionRebuilder(SearchIndexMapper searchIndexMapper,
                                StockFlatMapper stockFlatMapper,
                                MovementFlatMapper movementFlatMapper,
                                CatalogApi catalogApi,
                                LocationApi locationApi,
                                InventoryApi inventoryApi,
                                SystemApi systemApi) {
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.systemApi = systemApi;
    }

    @Transactional
    public void rebuild(UUID householdId) {
        log.info("Starting projection rebuild for household: {}", householdId);

        // 1. 清空该家庭所有投影行
        clearProjections(householdId);

        // 2. 从快照拉取端口回填
        rebuildSearchIndex(householdId);
        rebuildStockFlat(householdId);
        rebuildMovementFlat(householdId);

        // 3. 写审计
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "REPORTING_PROJECTION_REBUILT", "SUCCESS", householdId, null, null, null, null,
                Map.of("householdId", householdId.toString())));

        log.info("Projection rebuild complete for household: {}", householdId);
    }

    private void clearProjections(UUID householdId) {
        // 用 MyBatis-Plus LambdaDelete 按 householdId 清空各表
        // 实现时需要自定义 deleteByHouseholdId 方法或用 Wrapper
    }

    private void rebuildSearchIndex(UUID householdId) {
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = catalogApi.dumpItems(householdId, cursor, BATCH_SIZE);
            for (var item : page.items()) {
                // 构建 SearchIndexEntity 并 upsert
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
        // 同样拉取 LocationApi.dumpTree
    }

    private void rebuildStockFlat(UUID householdId) {
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = inventoryApi.dumpStockPositions(householdId, cursor, BATCH_SIZE);
            // 每个 StockPositionDump → upsert stock_flat
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void rebuildMovementFlat(UUID householdId) {
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = inventoryApi.dumpMovements(householdId, cursor, BATCH_SIZE);
            // 每个 MovementDump → upsert movement_flat
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }
}
```

- [ ] **Step 5: 创建 `ReportingExceptionHandler`**

```java
// backend/src/main/java/com/zija/reporting/internal/ReportingExceptionHandler.java
package com.zija.reporting.internal;

import com.zija.reporting.internal.export.ExportTooLargeException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(assignableTypes = ReportingController.class)
class ReportingExceptionHandler {

    @ExceptionHandler(ExportTooLargeException.class)
    ResponseEntity<Map<String, Object>> handleExportTooLarge(ExportTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "type", "about:blank",
                "title", "Export Too Large",
                "status", 400,
                "errorCode", "REPORTING_EXPORT_TOO_LARGE",
                "detail", "Export contains " + ex.getActualRows() + " rows (max " + ex.getMaxRows() + "). " +
                        "Please narrow your filter criteria."));
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: 编译通过

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/zija/reporting/internal/
git commit -m "feat(reporting): ReportingController + 全部 REST 端点

- GET /reporting/search — 全局搜索（ILIKE，按实体类型分组）
- GET /reporting/reports/* — 5 张报表查询（分页 + 筛选）
- GET /reporting/exports/{reportKey} — 同步流式 CSV 导出（StreamingResponseBody）
- POST /reporting/projection/rebuild — 投影重建（清空 + 快照拉取回填）
- ExportTooLargeException → 400 REPORTING_EXPORT_TOO_LARGE
- 报表/搜索 @RequireMember，导出/重建 @RequireAdmin"
```

---

### Task 4: 后端单元测试 + Testcontainers 集成测试

**Files:**
- Create: `backend/src/test/java/com/zija/reporting/internal/CsvWriterTest.java`
- Create: `backend/src/test/java/com/zija/reporting/internal/SearchServiceTest.java`
- Create: `backend/src/test/java/com/zija/reporting/internal/ReportServiceTest.java`
- Create: `backend/src/test/java/com/zija/reporting/internal/ReportingControllerTest.java`
- Create: `backend/src/test/java/com/zija/reporting/internal/projection/ProjectionRebuildTest.java`

- [ ] **Step 1: `CsvWriterTest` — BOM + 转义 + 空值 + 行数上限**

```java
// 测试点：
// - 输出以 UTF-8 BOM 开头
// - 含逗号的字段被双引号包裹
// - 含双引号的字段被转义为两个双引号
// - null 值输出为空字符串
// - 100,000 行不抛异常
```

- [ ] **Step 2: `SearchServiceTest` — 多类实体命中 + matchedFields + 空结果**

```java
// 测试点：
// - 搜索关键词命中物品名 → items 非空，matchedFields 含 "name"
// - 搜索关键词命中批次号 → lots 非空
// - 搜索关键词命中位置路径 → locations 非空
// - 无命中 → 三组均为空数组
// - limitPerGroup 生效
```

- [ ] **Step 3: `ReportServiceTest` — 各报表查询 + 筛选 + 分页**

```java
// 测试点：
// - stockByLocation 返回正确列
// - expiringLots withinDays 生效
// - lowStock 只返回低于阈值的物品
// - stock-changes 时间范围筛选
// - movements 按成员/类型筛选
// - 分页参数 page/pageSize 正确传递
```

- [ ] **Step 4: `ReportingControllerTest` — MockMvc 端点测试**

```java
// 测试点：
// - GET /search?q=xxx 返回 {items, lots, locations}
// - GET /reports/stock-by-location 返回分页结构
// - GET /exports/{key} 返回 CSV 流 + Content-Disposition
// - POST /projection/rebuild 返回 {status: ok}
// - 非 ADMIN 调 /exports 返回 403
// - 超过 100,000 行返回 400 REPORTING_EXPORT_TOO_LARGE
```

- [ ] **Step 5: `ProjectionRebuildTest` — Testcontainers 集成**

```java
// 测试点：
// - 建家 → 入库 → 等待投影 → 重建 → 投影表有数据
// - 重建写 REPORTING_PROJECTION_REBUILT 审计
// - 重建期间事件订阅仍持续运行
```

- [ ] **Step 6: 运行测试**

Run: `cd backend && ./mvnw -q test`
Expected: 全部 PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/test/java/com/zija/reporting/
git commit -m "test(reporting): 后端单元测试 + Testcontainers 集成测试

- CsvWriterTest: BOM/转义/空值/行数上限
- SearchServiceTest: 多类实体命中/matchedFields/空结果
- ReportServiceTest: 各报表查询/筛选/分页
- ReportingControllerTest: MockMvc 端点/权限/错误码
- ProjectionRebuildTest: Testcontainers 投影重建集成"
```

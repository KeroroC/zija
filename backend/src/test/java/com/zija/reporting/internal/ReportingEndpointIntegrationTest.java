package com.zija.reporting.internal;

import com.zija.TestDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zija.SharedPostgres;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reporting 报表/全局搜索端到端集成测试。
 *
 * <p>填补测试真空的两个核心断言：
 * <ol>
 *   <li>{@code ReportMapper.xml} / {@code SearchMapper.xml} 的 SQL 在真实 PostgreSQL 上执行
 *       （HAVING 阈值过滤、expiry 窗口、BETWEEN 时间窗、ILIKE 搜索、分页）。</li>
 *   <li>库存入库 → {@code StockChangedEvent} → AFTER_COMMIT 投影
 *       → {@code reporting_*_flat} 真实落库，报表读到的不是 mock 塞进去的数据。</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class ReportingEndpointIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ZoneId shanghai = ZoneId.of("Asia/Shanghai");

    private UUID householdId;
    private UUID ownerAccountId;
    private UUID memberAccountId;
    private UUID categoryId;
    private UUID unitId;
    private UUID itemLow;
    private UUID itemHigh;
    private UUID kitchenId;
    private UUID cabinetId;
    private ZijaPrincipal ownerPrincipal;
    private ZijaPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);

        householdId = seedHousehold();
        ownerAccountId = seedAccount("owner", "所有者");
        memberAccountId = seedAccount("member", "成员");
        seedMember(householdId, ownerAccountId, "OWNER");
        seedMember(householdId, memberAccountId, "MEMBER");

        categoryId = seedCategory(householdId, "日用品");
        unitId = seedUnit(householdId);
        itemLow = seedItem(householdId, categoryId, unitId, "洗衣液", "CUSTOM", "10");
        itemHigh = seedItem(householdId, categoryId, unitId, "洗洁精", "CUSTOM", "5");
        kitchenId = seedLocation(householdId, "厨房A");
        cabinetId = seedLocation(householdId, "储物柜B");

        ownerPrincipal = new ZijaPrincipal(ownerAccountId, "owner", "所有者", "hash", true);
        memberPrincipal = new ZijaPrincipal(memberAccountId, "member", "成员", "hash", true);
    }

    // ==================== 库存与位置分布（stockByLocation SQL） ====================

    @Test
    void stockByLocation_returnsProjectedRowsWithLotAndExpiry() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(7), "SN-001");
        inbound(itemHigh, 20, cabinetId, todayPlus(365), null);

        var resp = getJson("/api/v1/reporting/reports/stock-by-location");

        assertThat(resp.get("total").asInt()).isEqualTo(2);
        assertThat(resp.get("items")).hasSize(2);

        var lowRow = firstRow(resp, "item_name", "洗衣液");
        assertThat(lowRow.get("quantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(lowRow.get("item_id").asText()).isEqualTo(itemLow.toString());
        assertThat(lowRow.get("lot_number").asText()).isNotBlank();
        assertThat(shanghaiDate(lowRow.get("expiry_date"))).isEqualTo(todayPlus(7));
        assertThat(lowRow.get("location_path").asText()).isEqualTo("厨房A");

        var highRow = firstRow(resp, "item_name", "洗洁精");
        assertThat(highRow.get("quantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(highRow.get("location_path").asText()).isEqualTo("储物柜B");
    }

    @Test
    void stockByLocation_filtersByLocationAndItem() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(365), null);
        inbound(itemHigh, 12, cabinetId, todayPlus(365), null);

        var byLoc = getJson("/api/v1/reporting/reports/stock-by-location?locationId=" + kitchenId);
        assertThat(byLoc.get("total").asInt()).isEqualTo(1);
        assertThat(byLoc.get("items").get(0).get("item_name").asText()).isEqualTo("洗衣液");

        var byItem = getJson("/api/v1/reporting/reports/stock-by-location?itemId=" + itemHigh);
        assertThat(byItem.get("total").asInt()).isEqualTo(1);
        assertThat(byItem.get("items").get(0).get("item_name").asText()).isEqualTo("洗洁精");
    }

    // ==================== 临期批次（expiry_date 投影 + expiringLots SQL） ====================

    @Test
    void expiringLots_returnsOnlyLotsExpiringWithinWindow() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(7), "SN-001");
        inbound(itemHigh, 20, cabinetId, todayPlus(365), null);

        var within30 = getJson("/api/v1/reporting/reports/expiring-lots?withinDays=30");
        assertThat(within30.get("total").asInt()).isEqualTo(1);
        var row = within30.get("items").get(0);
        assertThat(row.get("item_name").asText()).isEqualTo("洗衣液");
        assertThat(shanghaiDate(row.get("expiry_date"))).isEqualTo(todayPlus(7));
        assertThat(row.get("lot_number").asText()).isNotBlank();
        assertThat(row.get("days_until_expiry").asInt()).isBetween(6, 8);

        // 窗口拉大到一年 → 两个批次都在
        var within400 = getJson("/api/v1/reporting/reports/expiring-lots?withinDays=400");
        assertThat(within400.get("total").asInt()).isEqualTo(2);
    }

    // ==================== 低库存（HAVING 阈值过滤，真实 SQL） ====================

    @Test
    void lowStock_onlyReturnsItemsBelowThreshold() throws Exception {
        // 洗衣液 5 < 阈值 10 → 低库存
        inbound(itemLow, 5, kitchenId, todayPlus(365), null);
        // 洗洁精 20 >= 阈值 5 → 被 HAVING 排除
        inbound(itemHigh, 20, cabinetId, todayPlus(365), null);

        var resp = getJson("/api/v1/reporting/reports/low-stock");
        assertThat(resp.get("total").asInt()).isEqualTo(1);
        var row = resp.get("items").get(0);
        assertThat(row.get("item_name").asText()).isEqualTo("洗衣液");
        assertThat(row.get("total_quantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(row.get("low_stock_threshold").decimalValue()).isEqualByComparingTo("10");
    }

    // ==================== 库存变化（stockChanges 时间窗 SQL） ====================

    @Test
    void stockChanges_filtersByBusinessTimeRange() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(365), null);

        var fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String from = OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1).format(fmt);
        String to = OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).format(fmt);
        var inRange = getJson("/api/v1/reporting/reports/stock-changes?from=" + from + "&to=" + to);
        assertThat(inRange.get("total").asInt()).isEqualTo(1);
        assertThat(inRange.get("items").get(0).get("type").asText()).isEqualTo("INBOUND");
        assertThat(inRange.get("items").get(0).get("item_name").asText()).isEqualTo("洗衣液");

        var outOfRange = getJson("/api/v1/reporting/reports/stock-changes"
                + "?from=2020-01-01T00:00:00Z&to=2020-01-31T00:00:00Z");
        assertThat(outOfRange.get("total").asInt()).isEqualTo(0);
    }

    // ==================== 流水（movements 类型/操作人过滤 SQL） ====================

    @Test
    void movements_filtersByTypeAndOperator() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(365), null);

        var byType = getJson("/api/v1/reporting/reports/movements?type=INBOUND");
        assertThat(byType.get("total").asInt()).isEqualTo(1);

        var wrongType = getJson("/api/v1/reporting/reports/movements?type=CONSUME");
        assertThat(wrongType.get("total").asInt()).isEqualTo(0);

        var byOperator = getJson("/api/v1/reporting/reports/movements?operatorAccountId=" + ownerAccountId);
        assertThat(byOperator.get("total").asInt()).isEqualTo(1);

        var wrongOperator = getJson("/api/v1/reporting/reports/movements?operatorAccountId=" + memberAccountId);
        assertThat(wrongOperator.get("total").asInt()).isEqualTo(0);
    }

    // ==================== 投影落库断言（denormalized 字段由真实模块解析） ====================

    @Test
    void movementProjection_resolvesNamesAndKeepsValues() throws Exception {
        inbound(itemLow, 5, kitchenId, todayPlus(7), "SN-001");
        awaitProjection("movement_flat", () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM reporting_movement_flat", Integer.class) == 1);

        var mov = jdbc.queryForMap(
                "SELECT item_name, type, quantity_delta, from_location_id, operator_display_name"
                        + " FROM reporting_movement_flat WHERE item_id = ?", itemLow);
        assertThat(mov.get("item_name")).isEqualTo("洗衣液");
        assertThat(mov.get("type")).isEqualTo("INBOUND");
        assertThat(((BigDecimal) mov.get("quantity_delta")).intValue()).isEqualTo(5);
        assertThat(mov.get("from_location_id")).isNull(); // INBOUND 无来源位
        assertThat(mov.get("operator_display_name")).isEqualTo("所有者");

        var stock = jdbc.queryForMap(
                "SELECT lot_number, serial_number, expiry_date, quantity FROM reporting_stock_flat WHERE item_id = ?",
                itemLow);
        assertThat(stock.get("lot_number")).isNotNull();
        assertThat(stock.get("serial_number")).isEqualTo("SN-001");
        assertThat(((java.sql.Date) stock.get("expiry_date")).toLocalDate()).isEqualTo(todayPlus(7));
        assertThat(((BigDecimal) stock.get("quantity")).intValue()).isEqualTo(5);
    }

    // ==================== 全局搜索（search_index + SearchMapper ILIKE SQL） ====================

    @Test
    void search_returnsItemsAndLocationsFromIndex() throws Exception {
        // search_index 写入路径由 ProjectionListener 集成测试覆盖；
        // 这里直接 seed 索引行，专注验证 SearchMapper/controller 的查询与 matchedFields 逻辑。
        jdbc.update("""
                INSERT INTO reporting_search_index
                    (household_id, entity_type, entity_id, item_name, brand_name, tag_names,
                     category_name, unit_name, updated_at)
                VALUES (?, 'ITEM', ?, '洗衣液', '蓝月亮', '清洁', '日用品', '瓶', CURRENT_TIMESTAMP),
                       (?, 'ITEM', ?, '洗洁精', '立白', '清洁', '日用品', '瓶', CURRENT_TIMESTAMP)
                """, householdId, UUID.randomUUID(), householdId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO reporting_search_index
                    (household_id, entity_type, entity_id, location_name, location_path, updated_at)
                VALUES (?, 'LOCATION', ?, '厨房A', '厨房A', CURRENT_TIMESTAMP)
                """, householdId, UUID.randomUUID());

        var itemHit = getJson("/api/v1/reporting/search?q=" + "洗衣");
        assertThat(itemHit.get("items")).hasSize(1);
        assertThat(itemHit.get("items").get(0).get("name").asText()).isEqualTo("洗衣液");
        assertThat(itemHit.get("items").get(0).get("matchedFields"))
                .anyMatch(f -> "name".equals(f.asText()));
        assertThat(itemHit.get("lots")).hasSize(0);
        assertThat(itemHit.get("locations")).hasSize(0);

        var locHit = getJson("/api/v1/reporting/search?q=" + "厨房");
        assertThat(locHit.get("locations")).hasSize(1);
        assertThat(locHit.get("locations").get(0).get("name").asText()).isEqualTo("厨房A");
        assertThat(locHit.get("locations").get(0).get("path").asText()).isEqualTo("厨房A");
        assertThat(locHit.get("items")).hasSize(0);

        var noHit = getJson("/api/v1/reporting/search?q=" + "不存在的关键词");
        assertThat(noHit.get("items")).hasSize(0);
        assertThat(noHit.get("lots")).hasSize(0);
        assertThat(noHit.get("locations")).hasSize(0);
    }

    // ==================== 权限与分页结构 ====================

    @Test
    void member_canReadReports_butCannotRebuildProjection() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/reports/stock-by-location")
                        .with(auth(memberPrincipal)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reporting/projection/rebuild")
                        .with(auth(memberPrincipal)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedReportsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/reports/stock-by-location"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_canRebuildProjection() throws Exception {
        mockMvc.perform(post("/api/v1/reporting/projection/rebuild")
                        .with(auth(ownerPrincipal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()));
    }

    @Test
    void reports_returnPageShape() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/reports/stock-by-location")
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    // ==================== 内部 helper ====================

    private void inbound(UUID itemId, long qty, UUID locId, LocalDate expiry, String serial)
            throws Exception {
        var parts = new ArrayList<String>();
        if (expiry != null) parts.add("\"expiryDate\": \"" + expiry + "\"");
        if (serial != null) parts.add("\"serialNumber\": \"" + serial + "\"");
        String meta = parts.isEmpty() ? "" : ",\n " + String.join(",\n ", parts);
        String body = """
                {
                    "itemId": "%s",
                    "quantity": %s,
                    "locationId": "%s"
                    %s
                }
                """.formatted(itemId, qty, locId, meta);

        mockMvc.perform(post("/api/v1/inventory/lots")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 事件在事务提交后异步派发 → 轮询等待该批次投影落库
        awaitProjection("lot " + itemId, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM reporting_stock_flat WHERE item_id = ?",
                Integer.class, itemId) >= 1);
    }

    private JsonNode getJson(String path) throws Exception {
        var res = mockMvc.perform(get(path).with(auth(ownerPrincipal)))
                .andReturn();
        if (res.getResponse().getStatus() != 200) {
            throw new AssertionError("GET " + path + " → " + res.getResponse().getStatus()
                    + " body=" + res.getResponse().getContentAsString());
        }
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static JsonNode firstRow(JsonNode page, String key, String value) {
        for (var row : page.get("items")) {
            if (row.has(key) && value.equals(row.get(key).asText())) return row;
        }
        throw new AssertionError("没有找到 " + key + "=" + value + " 的行");
    }

    private LocalDate todayPlus(int days) {
        return LocalDate.now(shanghai).plusDays(days);
    }

    /** 报表返回的 DATE 字段序列化为 UTC 瞬时，转换回上海日期用于断言。 */
    private LocalDate shanghaiDate(JsonNode node) {
        return OffsetDateTime.parse(node.asText()).atZoneSameInstant(shanghai).toLocalDate();
    }

    private void awaitProjection(String label, BooleanSupplier predicate) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("等待投影被中断: " + label);
            }
        }
        fail("等待投影超时(" + label + ")：事件未在 5s 内完成 AFTER_COMMIT 派发");
    }

    // ==================== Seed helpers（同 inventory 集成测试风格） ====================

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家" + h.getId().toString().substring(0, 6));
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedAccount(String username, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, 'ACTIVE')
                """, id, username, username.toUpperCase(), displayName);
        return id;
    }

    private void seedMember(UUID householdId, UUID accountId, String role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }

    private UUID seedCategory(UUID householdId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_category (id, household_id, name, name_normalized, status, sort_order, version)
                VALUES (?, ?, ?, ?, 'ACTIVE', 0, 1)
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedUnit(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "瓶" + id.toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedItem(UUID householdId, UUID categoryId, UUID unitId,
                          String name, String lowStockMode, String threshold) {
        UUID id = UUID.randomUUID();
        if (threshold == null) {
            jdbc.update("""
                    INSERT INTO catalog_item
                        (id, household_id, name, management_type, category_id, unit_id,
                         low_stock_mode, low_stock_threshold, status, version)
                    VALUES (?, ?, ?, 'CONSUMABLE', ?, ?, ?, NULL, 'ACTIVE', 1)
                    """, id, householdId, name, categoryId, unitId, lowStockMode);
        } else {
            jdbc.update("""
                    INSERT INTO catalog_item
                        (id, household_id, name, management_type, category_id, unit_id,
                         low_stock_mode, low_stock_threshold, status, version)
                    VALUES (?, ?, ?, 'CONSUMABLE', ?, ?, ?, ?, 'ACTIVE', 1)
                    """, id, householdId, name, categoryId, unitId, lowStockMode,
                    new BigDecimal(threshold));
        }
        return id;
    }

    private UUID seedLocation(UUID householdId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return user(principal);
    }
}
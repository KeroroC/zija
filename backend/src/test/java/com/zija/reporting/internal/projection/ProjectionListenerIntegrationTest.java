package com.zija.reporting.internal.projection;

import com.zija.TestDb;
import com.zija.SharedPostgres;
import com.zija.catalog.BrandChangedEvent;
import com.zija.catalog.CategoryChangedEvent;
import com.zija.catalog.ItemChangedEvent;
import com.zija.catalog.TagChangedEvent;
import com.zija.catalog.UnitChangedEvent;
import com.zija.inventory.StockChangedEvent;
import com.zija.location.LocationChangedEvent;
import com.zija.reporting.internal.persistence.MovementFlatMapper;
import com.zija.reporting.internal.persistence.ReportingDeadLetterMapper;
import com.zija.reporting.internal.persistence.ReportingProcessedEventMapper;
import com.zija.reporting.internal.persistence.SearchIndexMapper;
import com.zija.reporting.internal.persistence.StockFlatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProjectionListener} 七个事件处理器的直接集成测试（真实 Postgres）。
 *
 * <p>覆盖此前全零测试的逻辑：事件去重（幂等表）、movement_flat / stock_flat / search_index
 * 落库、ARCHIVED/DELETED 删除路径、失败写死信。命名解析（物品名/位置路径/操作人显示名）
 * 由真实模块（CatalogApi/LocationApi/IdentityApi）从种子数据解析，不 mock。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ProjectionListenerIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired ProjectionListener listener;
    @Autowired MovementFlatMapper movementFlatMapper;
    @Autowired StockFlatMapper stockFlatMapper;
    @Autowired SearchIndexMapper searchIndexMapper;
    @Autowired ReportingProcessedEventMapper processedEventMapper;
    @Autowired ReportingDeadLetterMapper deadLetterMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;
    private UUID accountId;
    private UUID categoryId;
    private UUID brandId;
    private UUID unitId;
    private UUID itemId;
    private UUID itemBId;
    private UUID locationId;
    private UUID lotId;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);

        householdId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        itemBId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        lotId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'testuser', 'testuser', 'hash', '测试用户', 'ACTIVE')
                """, accountId);
        jdbc.update("""
                INSERT INTO household (id, name, timezone) VALUES (?, '测试家庭', 'Asia/Shanghai')
                """, householdId);
        jdbc.update("""
                INSERT INTO member (id, household_id, account_id, role, status)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE')
                """, UUID.randomUUID(), householdId, accountId);

        jdbc.update("""
                INSERT INTO catalog_category (id, household_id, name, name_normalized, status, sort_order, version)
                VALUES (?, ?, '日用品', '日用品', 'ACTIVE', 0, 1)
                """, categoryId, householdId);
        jdbc.update("""
                INSERT INTO catalog_brand (id, household_id, name, name_normalized, status, version)
                VALUES (?, ?, '蓝月亮', '蓝月亮', 'ACTIVE', 1)
                """, brandId, householdId);
        jdbc.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, '瓶', '瓶', 0, 'ACTIVE')
                """, unitId, householdId);
        jdbc.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, category_id, brand_id, unit_id,
                                          status, version)
                VALUES (?, ?, '洗衣液', 'CONSUMABLE', ?, ?, ?, 'ACTIVE', 1)
                """, itemId, householdId, categoryId, brandId, unitId);
        jdbc.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, category_id, brand_id, unit_id,
                                          status, version)
                VALUES (?, ?, '洗洁精', 'CONSUMABLE', ?, ?, ?, 'ACTIVE', 1)
                """, itemBId, householdId, categoryId, brandId, unitId);

        jdbc.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, '厨房', '厨房', 0, true, 1)
                """, locationId, householdId);

        jdbc.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number, serial_number,
                                           expiry_date, created_at)
                VALUES (?, ?, ?, 'LOT-2026-001', 'SN-001', ?, CURRENT_TIMESTAMP)
                """, lotId, householdId, itemId, LocalDate.now().plusDays(30));
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id,
                                                      quantity, revision, updated_at)
                VALUES (?, ?, ?, ?, 3, 0, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), householdId, lotId, locationId);
    }

    // ==================== StockChangedEvent：movement_flat + stock_flat 投影 ====================

    @Test
    void stockChanged_projectsMovementFlatAndStockFlat() {
        listener.processStockChangedEvent(stockEvent("INBOUND", BigDecimal.valueOf(5), null, locationId, "采购"));

        var mov = movementFlatMapper.selectList(null).get(0);
        assertThat(mov.getMovementId()).isNotNull();
        assertThat(mov.getItemId()).isEqualTo(itemId);
        assertThat(mov.getItemName()).isEqualTo("洗衣液");
        assertThat(mov.getType()).isEqualTo("INBOUND");
        assertThat(mov.getQuantityDelta()).isEqualByComparingTo("5");
        assertThat(mov.getToLocationId()).isEqualTo(locationId);
        assertThat(mov.getToLocationPath()).isEqualTo("厨房");
        assertThat(mov.getOperatorAccountId()).isEqualTo(accountId);
        assertThat(mov.getOperatorDisplayName()).isEqualTo("测试用户");
        assertThat(mov.getReversalOf()).isNull();

        var s = stockFlatMapper.selectList(null).get(0);
        assertThat(s.getLotId()).isEqualTo(lotId);
        assertThat(s.getItemId()).isEqualTo(itemId);
        assertThat(s.getItemName()).isEqualTo("洗衣液");
        assertThat(s.getUnitName()).isEqualTo("瓶");
        assertThat(s.getLocationPath()).isEqualTo("厨房");
        assertThat(s.getQuantity()).isEqualByComparingTo("3");
        // 投影补上批次元数据：批次号 / 序列号 / 到期日
        assertThat(s.getLotNumber()).isEqualTo("LOT-2026-001");
        assertThat(s.getSerialNumber()).isEqualTo("SN-001");
        assertThat(s.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(30));

        assertThat(processedEventMapper.selectList(null)).hasSize(1);
    }

    @Test
    void stockChanged_reprocessSameEvent_isIdempotent() {
        var evt = stockEvent("INBOUND", BigDecimal.ONE, null, locationId, null);

        listener.processStockChangedEvent(evt);
        listener.processStockChangedEvent(evt);

        assertThat(movementFlatMapper.selectList(null)).hasSize(1);
        assertThat(stockFlatMapper.selectList(null)).hasSize(1);
        assertThat(processedEventMapper.selectList(null)).hasSize(1);
    }

    @Test
    void stockChanged_consumption_rebuildsQuantity() {
        listener.processStockChangedEvent(stockEvent("INBOUND", BigDecimal.valueOf(5), null, locationId, "采购"));

        // 领用 5 个单位 → 库存为 0（投影从源位置重新拉取当前 quantity）
        jdbc.update("UPDATE inventory_stock_position SET quantity = 0, updated_at = CURRENT_TIMESTAMP "
                + "WHERE household_id = ? AND lot_id = ?", householdId, lotId);
        listener.processStockChangedEvent(stockEvent("CONSUME", BigDecimal.valueOf(-5), locationId, null, "使用"));

        var s = stockFlatMapper.selectList(null).get(0);
        assertThat(s.getQuantity()).isEqualByComparingTo("0");
        assertThat(movementFlatMapper.selectList(null)).hasSize(2);
    }

    // ==================== Item 事件：search_index 增改删 ====================

    @Test
    void itemChanged_indexesItem_archivedDeletes() {
        listener.processItemChangedEvent(itemEvent("CREATED"));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getEntityType()).isEqualTo("ITEM");
        assertThat(row.getEntityId()).isEqualTo(itemId);
        assertThat(row.getItemName()).isEqualTo("洗衣液");
        assertThat(row.getCategoryName()).isEqualTo("日用品");
        assertThat(row.getBrandName()).isEqualTo("蓝月亮");
        assertThat(row.getUnitName()).isEqualTo("瓶");

        // 改名 → 重建后名称应更新
        jdbc.update("UPDATE catalog_item SET name = '衣物柔顺剂', version = version + 1 WHERE id = ?", itemId);
        listener.processItemChangedEvent(itemEvent("UPDATED"));
        var updated = searchIndexMapper.selectList(null);
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getItemName()).isEqualTo("衣物柔顺剂");

        // 归档 → 索引行删除
        listener.processItemChangedEvent(itemEvent("ARCHIVED"));
        assertThat(searchIndexMapper.selectList(null)).isEmpty();
    }

    // ==================== Category / Brand / Unit / Tag 事件：受影响物品重索引 ====================

    @Test
    void categoryChanged_reindexesItemsInCategory() {
        listener.processCategoryChangedEvent(new CategoryChangedEvent(
                UUID.randomUUID(), householdId, categoryId, "UPDATED"));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(e -> "日用品".equals(e.getCategoryName()));
    }

    @Test
    void brandChanged_reindexesItemsOfBrand() {
        listener.processBrandChangedEvent(new BrandChangedEvent(
                UUID.randomUUID(), householdId, brandId, "UPDATED"));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(e -> "蓝月亮".equals(e.getBrandName()));
    }

    @Test
    void tagChanged_reindexesAllItems() {
        // TagChangedEvent 不含物品列表 → 重建该家庭所有物品索引
        UUID tagId = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_tag (id, household_id, name, name_normalized, status) "
                + "VALUES (?, ?, '清洁', '清洁', 'ACTIVE')", tagId, householdId);
        jdbc.update("INSERT INTO catalog_item_tag (household_id, item_id, tag_id) "
                + "VALUES (?, ?, ?)", householdId, itemId, tagId);

        listener.processTagChangedEvent(new TagChangedEvent(
                UUID.randomUUID(), householdId, UUID.randomUUID(), "UPDATED"));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(e -> "ITEM".equals(e.getEntityType()));
    }

    @Test
    void unitChanged_reindexesItemsOfUnit() {
        listener.processUnitChangedEvent(new UnitChangedEvent(
                UUID.randomUUID(), householdId, unitId, "UPDATED"));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(e -> "瓶".equals(e.getUnitName()));
    }

    // ==================== Location 事件：LOCATION 索引增删 ====================

    @Test
    void locationChanged_indexesLocation_deletedRemoves() {
        listener.processLocationChangedEvent(new LocationChangedEvent(
                UUID.randomUUID(), householdId, locationId, "RENAMED", null, OffsetDateTime.now()));

        var rows = searchIndexMapper.selectList(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEntityType()).isEqualTo("LOCATION");
        assertThat(rows.get(0).getEntityId()).isEqualTo(locationId);
        assertThat(rows.get(0).getLocationName()).isEqualTo("厨房");
        assertThat(rows.get(0).getLocationPath()).isEqualTo("厨房");

        listener.processLocationChangedEvent(new LocationChangedEvent(
                UUID.randomUUID(), householdId, locationId, "DELETED", null, OffsetDateTime.now()));
        assertThat(searchIndexMapper.selectList(null)).isEmpty();
    }

    // ==================== 死信：处理失败 → dead-letter ====================

    @Test
    void stockChanged_failure_writesDeadLetter() {
        // 物品/批次都不存在 → 投影在 REQUIRES_NEW 中把事务标记 rollback-only → 监听器写死信
        var orphan = new StockChangedEvent(UUID.randomUUID(), householdId, UUID.randomUUID(),
                UUID.randomUUID(), "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID(), accountId, "x", null);

        listener.onStockChanged(orphan);

        var deadLetters = deadLetterMapper.selectList(null);
        assertThat(deadLetters).hasSize(1);
        var dl = deadLetters.get(0);
        assertThat(dl.getEventType()).isEqualTo("StockChangedEvent");
        assertThat(dl.getEventId()).isEqualTo(orphan.eventId());
        assertThat(dl.getFailureCount()).isEqualTo(1);
        assertThat(dl.getLastError()).isNotBlank();
        assertThat(dl.getAbandoned()).isFalse();
    }

    // ==================== helpers ====================

    private StockChangedEvent stockEvent(String type, BigDecimal delta,
                                          UUID fromLocationId, UUID toLocationId, String reason) {
        return new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, itemId, type, delta,
                fromLocationId, toLocationId, OffsetDateTime.now(),
                UUID.randomUUID(), UUID.randomUUID(), accountId, reason, null);
    }

    private ItemChangedEvent itemEvent(String changeType) {
        return new ItemChangedEvent(UUID.randomUUID(), householdId, itemId, changeType, OffsetDateTime.now());
    }
}
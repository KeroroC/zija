package com.zija.reporting.internal.projection;

import com.zija.reporting.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ProjectionRebuildTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired ProjectionRebuilder rebuilder;
    @Autowired SearchIndexMapper searchIndexMapper;
    @Autowired StockFlatMapper stockFlatMapper;
    @Autowired MovementFlatMapper movementFlatMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;
    private UUID accountId;
    private UUID itemId;
    private UUID unitId;
    private UUID categoryId;
    private UUID locationId;
    private UUID lotId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reporting_movement_flat, reporting_stock_flat, reporting_search_index, audit_log, inventory_movement, inventory_stock_position, inventory_lot, location, catalog_item, catalog_unit, catalog_category, member, household, account RESTART IDENTITY CASCADE");

        householdId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        lotId = UUID.randomUUID();

        // Seed account + household + member
        jdbc.update("INSERT INTO account (id, username, username_normalized, password_hash, display_name, status) VALUES (?, ?, ?, ?, ?, ?)",
                accountId, "testuser", "testuser", "hash", "测试用户", "ACTIVE");
        jdbc.update("INSERT INTO household (id, name, timezone) VALUES (?, ?, ?)",
                householdId, "测试家庭", "Asia/Shanghai");
        jdbc.update("INSERT INTO member (id, household_id, account_id, role, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), householdId, accountId, "ADMIN", "ACTIVE");

        // Seed catalog: category, unit, item
        jdbc.update("INSERT INTO catalog_category (id, household_id, name, name_normalized, status, sort_order, version) VALUES (?, ?, ?, ?, ?, ?, ?)",
                categoryId, householdId, "日用品", "日用品", "ACTIVE", 0, 1);
        jdbc.update("INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status) VALUES (?, ?, ?, ?, ?, ?)",
                unitId, householdId, "个", "个", 0, "ACTIVE");
        jdbc.update("INSERT INTO catalog_item (id, household_id, name, management_type, category_id, unit_id, status, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                itemId, householdId, "洗衣液", "CONSUMABLE", categoryId, unitId, "ACTIVE", 1);

        // Seed location (no status column)
        jdbc.update("INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version) VALUES (?, ?, ?, ?, ?, ?, ?)",
                locationId, householdId, "厨房", "厨房", 0, true, 1);

        // Seed lot
        jdbc.update("INSERT INTO inventory_lot (id, household_id, item_id, lot_number, created_at) VALUES (?, ?, ?, ?, ?)",
                lotId, householdId, itemId, "LOT-001", OffsetDateTime.now());

        // Seed stock position
        jdbc.update("INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), householdId, lotId, locationId, BigDecimal.TEN, 1L, OffsetDateTime.now());

        // Seed movement
        UUID movementId = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_movement (id, household_id, lot_id, item_id, type, quantity, from_location_id, to_location_id, operator_account_id, reason, business_time, created_at, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                movementId, householdId, lotId, itemId, "INBOUND", BigDecimal.TEN, null, locationId, accountId, "采购", OffsetDateTime.now(), OffsetDateTime.now(), UUID.randomUUID().toString());
    }

    @Test
    void rebuildPopulatesProjectionTables() {
        rebuilder.rebuild(householdId);

        // search index should have ITEM and LOCATION entries
        var searchRows = searchIndexMapper.selectList(null);
        assertThat(searchRows).hasSize(2);
        assertThat(searchRows).anyMatch(e -> "ITEM".equals(e.getEntityType()) && itemId.equals(e.getEntityId()));
        assertThat(searchRows).anyMatch(e -> "LOCATION".equals(e.getEntityType()) && locationId.equals(e.getEntityId()));

        // stock flat
        var stockRows = stockFlatMapper.selectList(null);
        assertThat(stockRows).hasSize(1);
        assertThat(stockRows.get(0).getLotId()).isEqualTo(lotId);
        assertThat(stockRows.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.TEN);

        // movement flat
        var movementRows = movementFlatMapper.selectList(null);
        assertThat(movementRows).hasSize(1);
        assertThat(movementRows.get(0).getType()).isEqualTo("INBOUND");
    }

    @Test
    void rebuildWritesAuditLog() {
        rebuilder.rebuild(householdId);

        var audits = jdbc.queryForList(
                "SELECT action, outcome FROM audit_log WHERE action = 'REPORTING_PROJECTION_REBUILT'");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).get("outcome")).isEqualTo("SUCCESS");
    }

    @Test
    void rebuildClearsOldProjectionsBeforeRepopulating() {
        // Insert stale data
        var staleSearch = new SearchIndexEntity();
        staleSearch.setHouseholdId(householdId);
        staleSearch.setEntityType("ITEM");
        staleSearch.setEntityId(UUID.randomUUID());
        staleSearch.setItemName("旧物品");
        staleSearch.setUpdatedAt(OffsetDateTime.now());
        searchIndexMapper.insert(staleSearch);

        var staleStock = new StockFlatEntity();
        staleStock.setHouseholdId(householdId);
        staleStock.setLotId(UUID.randomUUID());
        staleStock.setItemId(UUID.randomUUID());
        staleStock.setItemName("旧批次");
        staleStock.setUnitName("个");
        staleStock.setLocationId(UUID.randomUUID());
        staleStock.setLocationPath("旧位置");
        staleStock.setQuantity(BigDecimal.ONE);
        staleStock.setUpdatedAt(OffsetDateTime.now());
        stockFlatMapper.insert(staleStock);

        rebuilder.rebuild(householdId);

        // Old stale data should be gone, only new data present
        var searchRows = searchIndexMapper.selectList(null);
        assertThat(searchRows).noneMatch(e -> "旧物品".equals(e.getItemName()));
        assertThat(searchRows).hasSize(2);

        var stockRows = stockFlatMapper.selectList(null);
        assertThat(stockRows).noneMatch(e -> "旧批次".equals(e.getItemName()));
        assertThat(stockRows).hasSize(1);
    }
}

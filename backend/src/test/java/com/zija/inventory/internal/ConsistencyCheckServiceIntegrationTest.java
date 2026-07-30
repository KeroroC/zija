package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ConsistencyCheckServiceIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired MovementMapper movementMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired ConsistencyCheckService consistencyCheckService;
    @Autowired PlatformTransactionManager txManager;

    private UUID householdId;
    private UUID itemId;
    private UUID unitId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                TRUNCATE TABLE inventory_movement, inventory_stock_position,
                             inventory_lot, audit_log, catalog_item, catalog_unit,
                             location, household, account
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        unitId = seedUnit(householdId);
        itemId = seedItem(householdId, unitId);
        locationId = seedLocation(householdId);
    }

    // --- Test point 1: After normal inbound/consume, consistency check finds no discrepancies ---
    @Test
    void check_afterInboundAndConsume_noDiscrepancies() {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();

        // Inbound 10 units
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Consume 3 units
        newTx().executeWithoutResult(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(3), null, null, null));

        // Stock position: 10 - 3 = 7
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(7));

        // Consistency check should find no discrepancies
        List<ConsistencyCheckService.Discrepancy> discrepancies =
                newTx().execute(s -> consistencyCheckService.check(householdId, null));

        assertThat(discrepancies).isEmpty();
    }

    // --- Test point 2: Manually change stock_position.quantity → check discovers discrepancy, no DB writes ---
    @Test
    void check_tamperedStockPosition_findsDiscrepancyAndDoesNotModifyDb() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 10 units via service (creates consistent stock position + movement)
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Verify consistent before tampering
        List<ConsistencyCheckService.Discrepancy> before =
                newTx().execute(s -> consistencyCheckService.check(householdId, null));
        assertThat(before).isEmpty();

        // Tamper: directly update stock position quantity (bypass service)
        int rowsAffected = jdbc.update(
                "UPDATE inventory_stock_position SET quantity = 99 WHERE household_id = ? AND lot_id = ? AND location_id = ?",
                householdId, lotId, locationId);
        assertThat(rowsAffected).isEqualTo(1);

        // Consistency check should find the discrepancy
        List<ConsistencyCheckService.Discrepancy> after =
                newTx().execute(s -> consistencyCheckService.check(householdId, null));

        assertThat(after).hasSize(1);
        var disc = after.get(0);
        assertThat(disc.lotId()).isEqualTo(lotId);
        assertThat(disc.locationId()).isEqualTo(locationId);
        assertThat(disc.expected()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(disc.actual()).isEqualByComparingTo(BigDecimal.valueOf(99));

        // Verify no DB writes occurred: stock position quantity unchanged
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(99));

        // Verify no extra movements were created
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo("INBOUND");
    }

    // --- Test point 3: Filter by itemId only checks that item's stock positions ---
    @Test
    void check_filterByItemId_onlyReturnsDiscrepanciesForThatItem() {
        UUID itemId2 = seedItem(householdId, unitId);
        UUID lotId1 = seedLot(householdId, itemId);
        UUID lotId2 = seedLot(householdId, itemId2);

        // Inbound 10 units for item1
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId1, BigDecimal.TEN, null, null));

        // Inbound 5 units for item2
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId2, BigDecimal.valueOf(5), null, null));

        // Tamper only item2's stock position
        jdbc.update(
                "UPDATE inventory_stock_position SET quantity = 99 WHERE household_id = ? AND lot_id = ? AND location_id = ?",
                householdId, lotId2, locationId);

        // Filter by item1 → no discrepancies (item1 is consistent)
        List<ConsistencyCheckService.Discrepancy> item1Results =
                newTx().execute(s -> consistencyCheckService.check(householdId, itemId));
        assertThat(item1Results).isEmpty();

        // Filter by item2 → finds discrepancy
        List<ConsistencyCheckService.Discrepancy> item2Results =
                newTx().execute(s -> consistencyCheckService.check(householdId, itemId2));
        assertThat(item2Results).hasSize(1);
        var disc = item2Results.get(0);
        assertThat(disc.lotId()).isEqualTo(lotId2);
        assertThat(disc.locationId()).isEqualTo(locationId);
        assertThat(disc.expected()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(disc.actual()).isEqualByComparingTo(BigDecimal.valueOf(99));

        // No filter → finds discrepancy for item2 only
        List<ConsistencyCheckService.Discrepancy> allResults =
                newTx().execute(s -> consistencyCheckService.check(householdId, null));
        assertThat(allResults).hasSize(1);
        assertThat(allResults.get(0).lotId()).isEqualTo(lotId2);
    }

    // --- Helpers ---

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家");
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedUnit(UUID householdId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, "个" + id.toString().substring(0, 6), "个" + id.toString().substring(0, 6));
        return id;
    }

    private UUID seedItem(UUID householdId, UUID unitId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE')
                """, id, householdId, "物品" + id.toString().substring(0, 6), unitId);
        return id;
    }

    private UUID seedAccount() {
        UUID id = UUID.randomUUID();
        String username = "user" + id.toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name)
                VALUES (?, ?, ?, 'hash', '测试用户')
                """, id, username, username);
        return id;
    }

    private UUID seedLocation(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "位置" + id.toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedLot(UUID householdId, UUID itemId) {
        UUID id = UUID.randomUUID();
        String lotNumber = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%03d", (int) (Math.random() * 900) + 100);
        jdbc.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, householdId, itemId, lotNumber);
        return id;
    }

    private TransactionTemplate newTx() {
        return new TransactionTemplate(txManager);
    }
}

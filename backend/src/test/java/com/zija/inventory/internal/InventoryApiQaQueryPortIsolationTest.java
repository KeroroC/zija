package com.zija.inventory.internal;

import com.zija.SharedPostgres;
import com.zija.TestDb;
import com.zija.inventory.InventoryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventory 有界问答查询端口：家庭 A 的查询不得返回家庭 B 的批次、库存或流水。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InventoryApiQaQueryPortIsolationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired InventoryApi inventoryApi;
    @Autowired JdbcTemplate jdbc;

    private UUID householdA;
    private UUID householdB;
    private UUID itemA;
    private UUID itemB;
    private UUID locA;
    private UUID locB;
    private UUID expiringA;
    private UUID expiredA;
    private UUID blankSerialA;
    private UUID serialA;
    private UUID zeroQtyA;
    private UUID expiringB;
    private UUID serialB;
    private UUID movementA;
    private UUID movementB;
    private UUID operatorId;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        jdbc.execute("ALTER TABLE household DROP CONSTRAINT IF EXISTS ck_household_singleton");

        householdA = UUID.fromString("11000000-0000-0000-0000-00000000000a");
        householdB = UUID.fromString("11000000-0000-0000-0000-00000000000b");
        UUID unitA = UUID.fromString("31000000-0000-0000-0000-00000000000a");
        UUID unitB = UUID.fromString("31000000-0000-0000-0000-00000000000b");
        itemA = UUID.fromString("41000000-0000-0000-0000-00000000000a");
        itemB = UUID.fromString("41000000-0000-0000-0000-00000000000b");
        locA = UUID.fromString("61000000-0000-0000-0000-00000000000a");
        locB = UUID.fromString("61000000-0000-0000-0000-00000000000b");
        expiringA = UUID.fromString("51000000-0000-0000-0000-0000000000a1");
        expiredA = UUID.fromString("51000000-0000-0000-0000-0000000000a2");
        blankSerialA = UUID.fromString("51000000-0000-0000-0000-0000000000a3");
        serialA = UUID.fromString("51000000-0000-0000-0000-0000000000a4");
        zeroQtyA = UUID.fromString("51000000-0000-0000-0000-0000000000a5");
        expiringB = UUID.fromString("51000000-0000-0000-0000-0000000000b1");
        serialB = UUID.fromString("51000000-0000-0000-0000-0000000000b2");
        movementA = UUID.fromString("81000000-0000-0000-0000-00000000000a");
        movementB = UUID.fromString("81000000-0000-0000-0000-00000000000b");
        operatorId = UUID.fromString("21000000-0000-0000-0000-00000000000a");

        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (1, ?, 'A家', 'Asia/Shanghai')
                """, householdA);
        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (2, ?, 'B家', 'Asia/Shanghai')
                """, householdB);
        jdbc.update("""
                INSERT INTO account(id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'op-a', 'OP-A', 'hash', '操作人A', 'ACTIVE')
                """, operatorId);

        insertUnit(unitA, householdA, "瓶");
        insertUnit(unitB, householdB, "盒");
        insertItem(itemA, householdA, unitA, "牛奶", "CUSTOM", new BigDecimal("20"));
        insertItem(itemB, householdB, unitB, "牛奶", "CUSTOM", new BigDecimal("20"));
        insertLocation(locA, householdA, "厨房");
        insertLocation(locB, householdB, "厨房");

        insertLot(expiringA, householdA, itemA, "LOT-A-EXP", "SN-KEEP", TODAY.plusDays(5));
        insertLot(expiredA, householdA, itemA, "LOT-A-OLD", null, TODAY.minusDays(3));
        insertLot(blankSerialA, householdA, itemA, "LOT-A-BLANK", "", TODAY.plusDays(40));
        insertLot(serialA, householdA, itemA, "LOT-A-SN", "SN-COFFEE", TODAY.plusDays(40));
        insertLot(zeroQtyA, householdA, itemA, "LOT-A-ZERO", null, TODAY.plusDays(2));
        insertLot(expiringB, householdB, itemB, "LOT-B-EXP", "SN-KEEP", TODAY.plusDays(5));
        insertLot(serialB, householdB, itemB, "LOT-B-SN", "SN-COFFEE", TODAY.plusDays(40));

        insertPosition(householdA, expiringA, locA, "2");
        insertPosition(householdA, expiredA, locA, "3");
        insertPosition(householdA, blankSerialA, locA, "4");
        insertPosition(householdA, serialA, locA, "1");
        insertPosition(householdA, zeroQtyA, locA, "0");
        insertPosition(householdB, expiringB, locB, "9");
        insertPosition(householdB, serialB, locB, "8");

        insertMovement(movementA, householdA, expiringA, itemA, locA,
                OffsetDateTime.parse("2026-09-05T10:00:00+08:00"));
        insertMovement(movementB, householdB, expiringB, itemB, locB,
                OffsetDateTime.parse("2026-09-05T12:00:00+08:00"));
    }

    @AfterEach
    void restoreHouseholdSingleton() {
        TestDb.cleanAll(jdbc);
        jdbc.execute("ALTER TABLE household DROP CONSTRAINT IF EXISTS ck_household_singleton");
        jdbc.execute("ALTER TABLE household ADD CONSTRAINT ck_household_singleton CHECK (singleton_key = 1)");
    }

    @Test
    void findLotsMatchingQuestionDoesNotReturnOtherHouseholdOrBlankSerial() {
        var serialHits = inventoryApi.findLotsMatchingQuestion(householdA, "序列号 SN-COFFEE 在哪？", 10);
        assertThat(serialHits).extracting(InventoryApi.LotQuestionMatch::lotId).containsExactly(serialA);
        assertThat(serialHits).extracting(InventoryApi.LotQuestionMatch::itemName).containsExactly("牛奶");

        var unrelated = inventoryApi.findLotsMatchingQuestion(householdA, "厨房还有多少东西？", 10);
        assertThat(unrelated).extracting(InventoryApi.LotQuestionMatch::lotId)
                .doesNotContain(blankSerialA, serialB, expiringB);
    }

    @Test
    void findExpiringLotsStaysInsideHouseholdAndWindow() {
        var hits = inventoryApi.findExpiringLots(householdA, TODAY, TODAY.plusDays(7), null, null, 10);

        assertThat(hits).extracting(InventoryApi.LotQuantitySnapshot::lotId)
                .containsExactly(expiringA)
                .doesNotContain(expiredA, zeroQtyA, expiringB);
        assertThat(hits.getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(hits.getFirst().itemName()).isEqualTo("牛奶");
        assertThat(hits.getFirst().unitName()).isEqualTo("瓶");
    }

    @Test
    void findExpiredLotsStaysInsideHousehold() {
        var hits = inventoryApi.findExpiredLots(householdA, TODAY, null, null, 10);

        assertThat(hits).extracting(InventoryApi.LotQuantitySnapshot::lotId).containsExactly(expiredA);
        assertThat(hits.getFirst().quantity()).isEqualByComparingTo("3");
        assertThat(hits.getFirst().lotNumber()).isEqualTo("LOT-A-OLD");
    }

    @Test
    void findLowStockItemsDoesNotReturnOtherHousehold() {
        var hits = inventoryApi.findLowStockItems(householdA, null, 10);

        assertThat(hits).extracting(InventoryApi.LowStockSnapshot::itemId).containsExactly(itemA);
        assertThat(hits.getFirst().currentTotal()).isEqualByComparingTo("10");
        assertThat(hits.getFirst().threshold()).isEqualByComparingTo("20");
        assertThat(inventoryApi.findLowStockItems(householdB, null, 10))
                .extracting(InventoryApi.LowStockSnapshot::itemId)
                .containsExactly(itemB);
    }

    @Test
    void findStockPositionsInLocationsDoesNotLeakOtherHousehold() {
        var hits = inventoryApi.findStockPositionsInLocations(householdA, List.of(locA, locB), "牛奶", 20);

        assertThat(hits).extracting(InventoryApi.LocationStockPositionSnapshot::locationId).containsOnly(locA);
        assertThat(hits).extracting(InventoryApi.LocationStockPositionSnapshot::lotId)
                .contains(expiringA, expiredA, blankSerialA, serialA)
                .doesNotContain(expiringB, serialB, zeroQtyA);
    }

    @Test
    void findRecentMovementsOfItemDoesNotReturnOtherHousehold() {
        var hits = inventoryApi.findRecentMovementsOfItem(householdA, itemA, null, null, 10);

        assertThat(hits).extracting(InventoryApi.MovementInfo::id).containsExactly(movementA);
        assertThat(inventoryApi.findRecentMovementsOfItem(householdB, itemB, null, null, 10))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(movementB);
    }

    @Test
    void lotsOfItemIncludesLotNumberWithoutCrossingHouseholds() {
        var lots = inventoryApi.lotsOfItem(householdA, itemA);

        assertThat(lots).extracting(InventoryApi.LotInfo::lotId).contains(expiringA, serialA);
        assertThat(lots).filteredOn(lot -> expiringA.equals(lot.lotId()))
                .extracting(InventoryApi.LotInfo::lotNumber)
                .containsExactly("LOT-A-EXP");
        assertThat(inventoryApi.lotsOfItem(householdB, itemA)).isEmpty();
    }

    private void insertUnit(UUID id, UUID householdId, String name) {
        jdbc.update("""
                INSERT INTO catalog_unit(id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, name, name);
    }

    private void insertItem(
            UUID id, UUID householdId, UUID unitId, String name, String lowStockMode, BigDecimal threshold
    ) {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, low_stock_mode,
                     low_stock_threshold, status, version)
                VALUES (?, ?, ?, 'CONSUMABLE', ?, ?, ?, 'ACTIVE', 1)
                """, id, householdId, name, unitId, lowStockMode, threshold);
    }

    private void insertLocation(UUID id, UUID householdId, String name) {
        jdbc.update("""
                INSERT INTO location(id, household_id, parent_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, NULL, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
    }

    private void insertLot(
            UUID id, UUID householdId, UUID itemId, String lotNumber, String serial, LocalDate expiry
    ) {
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, expiry_date, lot_number, serial_number, version)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, id, householdId, itemId, expiry, lotNumber, serial);
    }

    private void insertPosition(UUID householdId, UUID lotId, UUID locationId, String quantity) {
        jdbc.update("""
                INSERT INTO inventory_stock_position(id, household_id, lot_id, location_id, quantity, revision)
                VALUES (?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), householdId, lotId, locationId, new BigDecimal(quantity));
    }

    private void insertMovement(
            UUID id, UUID householdId, UUID lotId, UUID itemId, UUID toLocation, OffsetDateTime businessTime
    ) {
        jdbc.update("""
                INSERT INTO inventory_movement
                    (id, household_id, lot_id, item_id, type, quantity, from_location_id,
                     to_location_id, reason, operator_account_id, business_time,
                     created_at, idempotency_key)
                VALUES (?, ?, ?, ?, 'INBOUND', '1', NULL, ?, '购入', ?, ?, ?, ?)
                """, id, householdId, lotId, itemId, toLocation, operatorId,
                Timestamp.from(businessTime.toInstant()), Timestamp.from(businessTime.toInstant()),
                id.toString());
    }
}

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
    void searchLotsByNumberOrSerialStaysInsideHouseholdAndIgnoresBlankSerial() {
        var bySerial = inventoryApi.searchLotsByNumberOrSerial(householdA, "sn-coffee", null, null, 10);
        assertThat(bySerial).extracting(InventoryApi.LotQuestionMatch::lotId).containsExactly(serialA);
        assertThat(bySerial).extracting(InventoryApi.LotQuestionMatch::itemId).containsExactly(itemA);
        assertThat(bySerial).extracting(InventoryApi.LotQuestionMatch::itemName).containsExactly("牛奶");

        var byLotNumber = inventoryApi.searchLotsByNumberOrSerial(householdA, "LOT-A-EXP", null, null, 10);
        assertThat(byLotNumber).extracting(InventoryApi.LotQuestionMatch::lotId).containsExactly(expiringA);

        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "LOT-B-EXP", null, null, 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "SN-COFFEE", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .doesNotContain(serialB);

        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "厨房还有多少东西？", null, null, 10))
                .isEmpty();

        var bounded = inventoryApi.searchLotsByNumberOrSerial(householdA, "LOT-A", null, null, 1);
        assertThat(bounded).hasSize(1);
        assertThat(bounded).extracting(InventoryApi.LotQuestionMatch::lotId).doesNotContain(serialB, expiringB);

        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "SN-COFFEE", itemB, null, 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "SN-COFFEE", null, expiringA, 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "  ", null, null, 2))
                .hasSize(2)
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .doesNotContain(expiringB, serialB);
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
    void findMovementsKeepsTimeWindowAndLocationInsideHousehold() {
        UUID pantry = UUID.fromString("61000000-0000-0000-0000-0000000000a2");
        insertLocation(pantry, householdA, "储藏室");

        UUID consumeFromKitchen = UUID.fromString("81000000-0000-0000-0000-0000000000c1");
        UUID oldInbound = UUID.fromString("81000000-0000-0000-0000-0000000000c2");
        UUID pantryInbound = UUID.fromString("81000000-0000-0000-0000-0000000000c3");
        UUID expiredLotInbound = UUID.fromString("81000000-0000-0000-0000-0000000000c4");
        UUID atExclusiveEnd = UUID.fromString("81000000-0000-0000-0000-0000000000c5");

        insertDirectionalMovement(consumeFromKitchen, householdA, expiringA, itemA, "CONSUME",
                locA, null, OffsetDateTime.parse("2026-09-10T08:00:00+08:00"));
        insertDirectionalMovement(oldInbound, householdA, expiringA, itemA, "INBOUND",
                null, locA, OffsetDateTime.parse("2026-06-01T08:00:00+08:00"));
        insertDirectionalMovement(pantryInbound, householdA, expiringA, itemA, "INBOUND",
                null, pantry, OffsetDateTime.parse("2026-09-08T09:00:00+08:00"));
        insertDirectionalMovement(expiredLotInbound, householdA, expiredA, itemA, "INBOUND",
                null, locA, OffsetDateTime.parse("2026-09-09T09:00:00+08:00"));
        insertDirectionalMovement(atExclusiveEnd, householdA, expiringA, itemA, "INBOUND",
                null, locA, OffsetDateTime.parse("2026-09-11T00:00:00+08:00"));

        var from = OffsetDateTime.parse("2026-09-01T00:00:00+08:00");
        var to = OffsetDateTime.parse("2026-09-11T00:00:00+08:00");

        assertThat(inventoryApi.findMovements(householdA, itemA, null, List.of(locA), from, to, 10))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(consumeFromKitchen, expiredLotInbound, movementA);
        assertThat(inventoryApi.findMovements(householdA, itemA, expiredA, List.of(locA), from, to, 10))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(expiredLotInbound);
        assertThat(inventoryApi.findMovements(householdA, null, null, List.of(pantry), from, to, 10))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(pantryInbound);
        assertThat(inventoryApi.findMovements(householdA, itemA, null, null, from, to, 1))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(consumeFromKitchen);

        assertThat(inventoryApi.findMovements(householdA, null, null, List.of(locB), from, to, 10)).isEmpty();
        assertThat(inventoryApi.findMovements(householdA, itemB, null, null, from, to, 10)).isEmpty();
        assertThat(inventoryApi.findMovements(householdA, null, null, List.of(), from, to, 10)).isEmpty();
        assertThat(inventoryApi.findMovements(householdA, null, null, null, null, null, 10)).isEmpty();
        assertThat(inventoryApi.findMovements(householdB, itemB, null, null, from, to, 10))
                .extracting(InventoryApi.MovementInfo::id)
                .containsExactly(movementB);
    }

    @Test
    void locationFilteredExpiryQueriesCountOnlyThosePlacesAndStayInsideHousehold() {
        UUID pantry = UUID.fromString("61000000-0000-0000-0000-0000000000a2");
        insertLocation(pantry, householdA, "储藏室");
        insertPosition(householdA, expiringA, pantry, "5");
        insertPosition(householdA, expiredA, pantry, "4");

        var expiringHere = inventoryApi.findExpiringLotsInLocations(
                householdA, TODAY, TODAY.plusDays(7), List.of(locA), 10);
        assertThat(expiringHere).extracting(InventoryApi.LotQuantitySnapshot::lotId).containsExactly(expiringA);
        assertThat(expiringHere.getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(expiringHere.getFirst().unitName()).isEqualTo("瓶");

        var expiringMixedIds = inventoryApi.findExpiringLotsInLocations(
                householdA, TODAY, TODAY.plusDays(7), List.of(locA, locB), 10);
        assertThat(expiringMixedIds).extracting(InventoryApi.LotQuantitySnapshot::lotId).containsExactly(expiringA);
        assertThat(expiringMixedIds.getFirst().quantity()).isEqualByComparingTo("2");

        assertThat(inventoryApi.findExpiringLotsInLocations(
                householdA, TODAY, TODAY.plusDays(7), List.of(locB), 10)).isEmpty();
        assertThat(inventoryApi.findExpiringLotsInLocations(
                householdA, TODAY, TODAY.plusDays(7), List.of(), 10)).isEmpty();

        var expiringHousehold = inventoryApi.findExpiringLots(
                householdA, TODAY, TODAY.plusDays(7), null, null, 10);
        assertThat(expiringHousehold).extracting(InventoryApi.LotQuantitySnapshot::lotId).containsExactly(expiringA);
        assertThat(expiringHousehold.getFirst().quantity()).isEqualByComparingTo("7");

        var expiredHere = inventoryApi.findExpiredLotsInLocations(householdA, TODAY, List.of(locA), 10);
        assertThat(expiredHere).extracting(InventoryApi.LotQuantitySnapshot::lotId).containsExactly(expiredA);
        assertThat(expiredHere.getFirst().quantity()).isEqualByComparingTo("3");
        assertThat(expiredHere.getFirst().lotNumber()).isEqualTo("LOT-A-OLD");

        assertThat(inventoryApi.findExpiredLotsInLocations(householdA, TODAY, List.of(locB), 10)).isEmpty();
        assertThat(inventoryApi.findExpiredLots(householdA, TODAY, null, null, 10).getFirst().quantity())
                .isEqualByComparingTo("7");
    }

    @Test
    void searchLotsByNumberOrSerialInLocationsStaysInsideHouseholdAndPlace() {
        UUID pantry = UUID.fromString("61000000-0000-0000-0000-0000000000a2");
        UUID bedroom = UUID.fromString("61000000-0000-0000-0000-0000000000a3");
        insertLocation(pantry, householdA, "储藏室");
        insertLocation(bedroom, householdA, "卧室");

        UUID inPantry = UUID.fromString("51000000-0000-0000-0000-0000000000aa");
        UUID alsoInPantry = UUID.fromString("51000000-0000-0000-0000-0000000000ad");
        UUID inBedroom = UUID.fromString("51000000-0000-0000-0000-0000000000ab");
        UUID zeroInPantry = UUID.fromString("51000000-0000-0000-0000-0000000000ac");
        UUID foreign = UUID.fromString("51000000-0000-0000-0000-0000000000ba");

        insertLot(inPantry, householdA, itemA, "LOT-PLACE-IN", "SN-ONLY-IN", TODAY.plusDays(10));
        insertLot(alsoInPantry, householdA, itemA, "LOT-PLACE-IN-2", "SN-PLACE-IN-2", TODAY.plusDays(10));
        insertLot(inBedroom, householdA, itemA, "LOT-PLACE-OUT", "SN-PLACE-OUT", TODAY.plusDays(10));
        insertLot(zeroInPantry, householdA, itemA, "LOT-PLACE-ZERO", "SN-PLACE-ZERO", TODAY.plusDays(10));
        insertLot(foreign, householdB, itemB, "LOT-PLACE-B", "SN-ONLY-IN", TODAY.plusDays(10));

        insertPosition(householdA, inPantry, pantry, "3");
        insertPosition(householdA, alsoInPantry, pantry, "1");
        insertPosition(householdA, inBedroom, bedroom, "4");
        insertPosition(householdA, zeroInPantry, pantry, "0");
        insertPosition(householdB, foreign, locB, "8");

        var inPlace = inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "LOT-PLACE", List.of(pantry), 10);
        assertThat(inPlace).extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(inPantry, alsoInPantry);
        assertThat(inPlace).extracting(InventoryApi.LotQuestionMatch::lotNumber)
                .containsExactly("LOT-PLACE-IN", "LOT-PLACE-IN-2");
        assertThat(inPlace).extracting(InventoryApi.LotQuestionMatch::serialNumber)
                .containsExactly("SN-ONLY-IN", "SN-PLACE-IN-2");
        assertThat(inPlace.getFirst().itemName()).isEqualTo("牛奶");

        var bySerial = inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "sn-only-in", List.of(pantry, locB), 10);
        assertThat(bySerial).extracting(InventoryApi.LotQuestionMatch::lotId).containsExactly(inPantry);
        assertThat(bySerial).extracting(InventoryApi.LotQuestionMatch::serialNumber)
                .containsExactly("SN-ONLY-IN");

        var bounded = inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "LOT-PLACE", List.of(pantry), 1);
        assertThat(bounded).extracting(InventoryApi.LotQuestionMatch::lotId).containsExactly(inPantry);

        assertThat(inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "LOT-PLACE", List.of(), 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "SN-ONLY-IN", List.of(locB), 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerialInLocations(
                householdA, "LOT-PLACE-OUT", List.of(pantry), 10)).isEmpty();
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "LOT-PLACE-OUT", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(inBedroom);
    }

    @Test
    void searchLotsByNumberOrSerialTreatsLikeWildcardsAsLiteralsAndStaysInsideHousehold() {
        UUID underscoreA = UUID.fromString("51000000-0000-0000-0000-0000000000a6");
        UUID lookalikeA = UUID.fromString("51000000-0000-0000-0000-0000000000a7");
        UUID percentLotA = UUID.fromString("51000000-0000-0000-0000-0000000000a8");
        UUID percentSerialA = UUID.fromString("51000000-0000-0000-0000-0000000000a9");
        UUID underscoreB = UUID.fromString("51000000-0000-0000-0000-0000000000b3");
        UUID percentB = UUID.fromString("51000000-0000-0000-0000-0000000000b4");

        insertLot(underscoreA, householdA, itemA, "LOT_01", "SN_A1", TODAY.plusDays(10));
        insertLot(lookalikeA, householdA, itemA, "LOTX01", "SNXA1", TODAY.plusDays(10));
        insertLot(percentLotA, householdA, itemA, "LOT%A", null, TODAY.plusDays(10));
        insertLot(percentSerialA, householdA, itemA, "NOPCT-A", "SN%1", TODAY.plusDays(10));
        insertLot(underscoreB, householdB, itemB, "XLOT_01", "SN_A1", TODAY.plusDays(10));
        insertLot(percentB, householdB, itemB, "BLOT%1", "SN%1", TODAY.plusDays(10));

        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "LOT_01", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(underscoreA);
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "lot_01", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(underscoreA);
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "SN_A1", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(underscoreA);

        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "%", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(percentLotA, percentSerialA);
        assertThat(inventoryApi.searchLotsByNumberOrSerial(householdA, "SN%", null, null, 10))
                .extracting(InventoryApi.LotQuestionMatch::lotId)
                .containsExactly(percentSerialA);
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
        insertDirectionalMovement(id, householdId, lotId, itemId, "INBOUND", null, toLocation, businessTime);
    }

    private void insertDirectionalMovement(
            UUID id,
            UUID householdId,
            UUID lotId,
            UUID itemId,
            String type,
            UUID fromLocation,
            UUID toLocation,
            OffsetDateTime businessTime
    ) {
        jdbc.update("""
                INSERT INTO inventory_movement
                    (id, household_id, lot_id, item_id, type, quantity, from_location_id,
                     to_location_id, reason, operator_account_id, business_time,
                     created_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, '1', ?, ?, '购入', ?, ?, ?, ?)
                """, id, householdId, lotId, itemId, type, fromLocation, toLocation, operatorId,
                Timestamp.from(businessTime.toInstant()), Timestamp.from(businessTime.toInstant()),
                id.toString());
    }
}

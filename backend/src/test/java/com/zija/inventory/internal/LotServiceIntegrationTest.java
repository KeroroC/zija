package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.LotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class LotServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired LotService lotService;
    @Autowired PlatformTransactionManager txManager;

    private static final UUID ARCHIVED_ITEM_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private UUID householdId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                TRUNCATE TABLE inventory_movement, inventory_stock_position,
                             inventory_lot, catalog_item, catalog_unit, household
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        UUID unitId = seedUnit(householdId);
        itemId = seedItem(householdId, unitId);
    }

    // --- Test point 1: createLot validates item is active ---
    @Test
    void createLot_archivedItem_throwsArchivedItemException() {
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        lotService.createLot(householdId, ARCHIVED_ITEM_ID,
                                LocalDate.now(), null, null, null, null, null))
        ).isInstanceOf(InventoryArchivedItemException.class);
    }

    // --- Test point 2: updateLotMeta version conflict ---
    @Test
    void updateLotMeta_staleVersion_throwsLotVersionConflict() {
        UUID lotId = (UUID) newTx().execute(s ->
                lotService.createLot(householdId, itemId,
                        LocalDate.now(), null, null, "LOT-001", null, null));

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        lotService.updateLotMeta(householdId, lotId, 99,
                                LocalDate.now(), null, null, "LOT-002", null, null))
        ).isInstanceOf(InventoryLotVersionConflictException.class);
    }

    // --- Test point 2 (cont): after success, version +1 ---
    @Test
    void updateLotMeta_success_versionIncremented() {
        UUID lotId = (UUID) newTx().execute(s ->
                lotService.createLot(householdId, itemId,
                        LocalDate.now(), null, null, null, null, null));

        var updated = (LotEntity) newTx().execute(s ->
                lotService.updateLotMeta(householdId, lotId, 0,
                        LocalDate.of(2024, 1, 1), null, null, "LOT-UPD", "SN-UPD", "备忘"));

        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getLotNumber()).isEqualTo("LOT-UPD");
        assertThat(updated.getSerialNumber()).isEqualTo("SN-UPD");
        assertThat(updated.getMemo()).isEqualTo("备忘");
    }

    // --- Test point 3: updateLotMeta never changes item_id ---
    @Test
    void updateLotMeta_neverTouchesItemId() {
        UUID lotId = (UUID) newTx().execute(s ->
                lotService.createLot(householdId, itemId,
                        LocalDate.now(), null, null, null, null, null));

        UUID anotherItemId = seedItem(householdId, seedUnit(householdId));

        // updateLotMeta only sets allowed fields; item_id is never in the SET clause
        var updated = (LotEntity) newTx().execute(s ->
                lotService.updateLotMeta(householdId, lotId, 0,
                        null, null, null, null, null, "should not change item"));

        assertThat(updated.getItemId()).isEqualTo(itemId);
        // Verify version still incremented (update did execute)
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    // --- Test point 4: serialNumberDuplicated ---
    @Test
    void serialNumberDuplicated_sameItemSameSerial_returnsTrue() {
        newTx().executeWithoutResult(s ->
                lotService.createLot(householdId, itemId,
                        null, null, null, null, "SN-001", null));

        Boolean duplicated = (Boolean) newTx().execute(s ->
                lotService.serialNumberDuplicated(householdId, itemId, "SN-001"));
        assertThat(duplicated).isTrue();
    }

    @Test
    void serialNumberDuplicated_differentItem_returnsFalse() {
        newTx().executeWithoutResult(s ->
                lotService.createLot(householdId, itemId,
                        null, null, null, null, "SN-001", null));

        UUID anotherItemId = seedItem(householdId, seedUnit(householdId));

        Boolean duplicated = (Boolean) newTx().execute(s ->
                lotService.serialNumberDuplicated(householdId, anotherItemId, "SN-001"));
        assertThat(duplicated).isFalse();
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

    private TransactionTemplate newTx() {
        return new TransactionTemplate(txManager);
    }
}

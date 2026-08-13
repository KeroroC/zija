package com.zija.inventory.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.internal.exception.InventoryArchivedItemException;
import com.zija.inventory.internal.exception.InventoryLotNotFoundException;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.LotMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExistingLotQuantityTest {

    private final LotMapper lotMapper = mock(LotMapper.class);
    private final CatalogApi catalogApi = mock(CatalogApi.class);
    private final UUID householdId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @Test
    void require_throwsWhenLotMissing() {
        when(lotMapper.selectById(lotId)).thenReturn(null);

        assertThatThrownBy(() -> ExistingLotQuantity.require(
                lotMapper, catalogApi, householdId, lotId, BigDecimal.ONE))
                .isInstanceOf(InventoryLotNotFoundException.class);
    }

    @Test
    void require_throwsWhenLotBelongsToAnotherHousehold() {
        var lot = new LotEntity();
        lot.setHouseholdId(UUID.randomUUID());
        lot.setItemId(itemId);
        when(lotMapper.selectById(lotId)).thenReturn(lot);

        assertThatThrownBy(() -> ExistingLotQuantity.require(
                lotMapper, catalogApi, householdId, lotId, BigDecimal.ONE))
                .isInstanceOf(InventoryLotNotFoundException.class);
    }

    @Test
    void require_throwsWhenItemMissing() {
        stubLot();
        when(catalogApi.requireItem(householdId, itemId)).thenThrow(new RuntimeException("missing"));

        assertThatThrownBy(() -> ExistingLotQuantity.require(
                lotMapper, catalogApi, householdId, lotId, BigDecimal.ONE))
                .isInstanceOf(InventoryArchivedItemException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void require_returnsItemIdAndScaledQuantity() {
        stubLot();
        CatalogApi.ItemInfo itemInfo = mock(CatalogApi.ItemInfo.class);
        when(itemInfo.unitId()).thenReturn(unitId);
        when(catalogApi.requireItem(householdId, itemId)).thenReturn(itemInfo);
        when(catalogApi.requireUnit(householdId, unitId))
                .thenReturn(new CatalogApi.UnitInfo(unitId, householdId, "个", 0, "ACTIVE"));

        ExistingLotQuantity.Prepared prepared = ExistingLotQuantity.require(
                lotMapper, catalogApi, householdId, lotId, new BigDecimal("2"));

        assertThat(prepared.itemId()).isEqualTo(itemId);
        assertThat(prepared.validatedQty()).isEqualByComparingTo(new BigDecimal("2"));
    }

    private void stubLot() {
        var lot = new LotEntity();
        lot.setHouseholdId(householdId);
        lot.setItemId(itemId);
        when(lotMapper.selectById(lotId)).thenReturn(lot);
    }
}

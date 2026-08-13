package com.zija.inventory.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.internal.exception.InventoryArchivedItemException;
import com.zija.inventory.internal.exception.InventoryLotNotFoundException;
import com.zija.inventory.internal.persistence.LotMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 领用 / 报损 / 移位共用的批次加载与数量精度校验（允许归档物品）。
 */
final class ExistingLotQuantity {

    private ExistingLotQuantity() {}

    record Prepared(UUID itemId, BigDecimal validatedQty) {}

    static Prepared require(LotMapper lotMapper, CatalogApi catalogApi,
                            UUID householdId, UUID lotId, BigDecimal quantity) {
        var lot = lotMapper.selectById(lotId);
        if (lot == null || !lot.getHouseholdId().equals(householdId)) {
            throw new InventoryLotNotFoundException();
        }
        UUID itemId = lot.getItemId();
        CatalogApi.ItemInfo itemInfo;
        try {
            itemInfo = catalogApi.requireItem(householdId, itemId);
        } catch (RuntimeException ex) {
            throw new InventoryArchivedItemException("item is missing: " + itemId);
        }
        var unitInfo = catalogApi.requireUnit(householdId, itemInfo.unitId());
        BigDecimal validatedQty = QuantityPrecision.require(unitInfo.decimalScale(), quantity);
        return new Prepared(itemId, validatedQty);
    }
}

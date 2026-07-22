package com.zija.catalog;

import java.util.UUID;

public interface CatalogApi {

    ItemInfo requireItem(UUID householdId, UUID itemId);

    ItemInfo requireActiveItem(UUID householdId, UUID itemId);

    UnitInfo requireUnit(UUID householdId, UUID unitId);

    record ItemInfo(
            UUID id,
            UUID householdId,
            String name,
            String managementType,
            UUID categoryId,
            UUID brandId,
            UUID unitId,
            UUID coverFileId,
            String status
    ) {
    }

    record UnitInfo(
            UUID id,
            UUID householdId,
            String name,
            int decimalScale,
            String status
    ) {
    }
}

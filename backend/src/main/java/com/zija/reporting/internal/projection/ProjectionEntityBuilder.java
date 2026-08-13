package com.zija.reporting.internal.projection;

import com.zija.catalog.CatalogApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import com.zija.reporting.internal.persistence.MovementFlatEntity;
import com.zija.reporting.internal.persistence.SearchIndexEntity;
import com.zija.reporting.internal.persistence.StockFlatEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 投影实体构建器。{@link ProjectionListener} 与 {@link ProjectionRebuilder} 共享的
 * reporting_* 实体组装逻辑，避免两处重复实现。
 */
@Component
class ProjectionEntityBuilder {

    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final IdentityApi identityApi;

    ProjectionEntityBuilder(CatalogApi catalogApi,
                            LocationApi locationApi,
                            IdentityApi identityApi) {
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.identityApi = identityApi;
    }

    SearchIndexEntity buildItemSearchIndex(UUID householdId, CatalogApi.ItemFlat item) {
        var e = new SearchIndexEntity();
        e.setHouseholdId(householdId);
        e.setEntityType("ITEM");
        e.setEntityId(item.itemId());
        e.setItemName(item.name());
        e.setBrandName(item.brandName());
        e.setTagNames(item.tagNames());
        e.setCategoryName(item.categoryName());
        e.setUnitName(item.unitName());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    SearchIndexEntity buildLocationSearchIndex(UUID householdId, LocationApi.LocationFlat loc) {
        var e = new SearchIndexEntity();
        e.setHouseholdId(householdId);
        e.setEntityType("LOCATION");
        e.setEntityId(loc.locationId());
        e.setLocationName(loc.name());
        e.setLocationPath(loc.path());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    StockFlatEntity buildStockFlat(UUID householdId, UUID lotId, UUID itemId, String itemName,
                                   String unitName, InventoryApi.LotFlat lot,
                                   UUID locationId, BigDecimal quantity) {
        var e = new StockFlatEntity();
        e.setHouseholdId(householdId);
        e.setLotId(lotId);
        e.setItemId(itemId);
        e.setItemName(itemName);
        e.setUnitName(unitName);
        if (lot != null) {
            e.setLotNumber(lot.lotNumber());
            e.setSerialNumber(lot.serialNumber());
            e.setExpiryDate(lot.expiryDate());
        }
        e.setLocationId(locationId);
        e.setLocationPath(resolveLocationPath(householdId, locationId));
        e.setQuantity(quantity);
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    MovementFlatEntity buildMovementFlat(UUID householdId,
                                         UUID movementId, UUID eventId,
                                         UUID lotId, UUID itemId, String type,
                                         BigDecimal quantityDelta,
                                         UUID fromLocationId, UUID toLocationId,
                                         UUID operatorAccountId, String reason,
                                         UUID reversalOf, OffsetDateTime businessTime) {
        var e = new MovementFlatEntity();
        e.setHouseholdId(householdId);
        e.setMovementId(movementId);
        e.setEventId(eventId);
        e.setLotId(lotId);
        e.setItemId(itemId);
        e.setItemName(resolveItemName(householdId, itemId));
        e.setType(type);
        e.setQuantityDelta(quantityDelta);
        e.setFromLocationId(fromLocationId);
        e.setToLocationId(toLocationId);
        e.setFromLocationPath(resolveLocationPath(householdId, fromLocationId));
        e.setToLocationPath(resolveLocationPath(householdId, toLocationId));
        e.setOperatorAccountId(operatorAccountId);
        e.setOperatorDisplayName(resolveDisplayName(operatorAccountId));
        e.setReason(reason);
        e.setReversalOf(reversalOf);
        e.setBusinessTime(businessTime);
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    String resolveItemName(UUID householdId, UUID itemId) {
        try {
            var item = catalogApi.requireItem(householdId, itemId);
            return item.name();
        } catch (Exception e) {
            return itemId.toString();
        }
    }

    String resolveUnitName(UUID householdId, UUID unitId) {
        if (unitId == null) return null;
        try {
            var unit = catalogApi.requireUnit(householdId, unitId);
            return unit.name();
        } catch (Exception e) {
            return unitId.toString();
        }
    }

    String resolveItemUnitName(UUID householdId, UUID itemId) {
        try {
            var item = catalogApi.requireItem(householdId, itemId);
            if (item.unitId() == null) return null;
            var unit = catalogApi.requireUnit(householdId, item.unitId());
            return unit.name();
        } catch (Exception e) {
            return null;
        }
    }

    String resolveLocationPath(UUID householdId, UUID locationId) {
        if (locationId == null) return null;
        try {
            var loc = locationApi.requireLocation(householdId, locationId);
            return loc.name();
        } catch (Exception e) {
            return locationId.toString();
        }
    }

    String resolveDisplayName(UUID accountId) {
        if (accountId == null) return null;
        try {
            var account = identityApi.findById(accountId);
            return account.map(IdentityApi.AccountInfo::displayName).orElse(accountId.toString());
        } catch (Exception e) {
            return accountId.toString();
        }
    }
}

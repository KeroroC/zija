package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存位写入辅助：锁定已有行，不存在则插入数量为 0 的新行。
 */
final class StockPositions {

    private StockPositions() {}

    static StockPositionEntity lockOrCreate(StockPositionMapper mapper,
                                            UUID householdId, UUID lotId, UUID locationId) {
        StockPositionEntity sp = mapper.lockOne(householdId, lotId, locationId);
        if (sp == null) {
            sp = new StockPositionEntity();
            sp.setId(UUID.randomUUID());
            sp.setHouseholdId(householdId);
            sp.setLotId(lotId);
            sp.setLocationId(locationId);
            sp.setQuantity(BigDecimal.ZERO);
            sp.setRevision(0L);
            sp.setCreatedAt(OffsetDateTime.now());
            sp.setUpdatedAt(OffsetDateTime.now());
            mapper.insert(sp);
        }
        return sp;
    }
}

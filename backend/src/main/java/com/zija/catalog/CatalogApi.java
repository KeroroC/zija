package com.zija.catalog;

import java.util.UUID;

/**
 * 品目模块公共 API，提供物品和计量单位的存在性校验与信息查询能力。
 */
public interface CatalogApi {

    /** 获取指定家庭下的物品信息，不存在则抛出异常。 */
    ItemInfo requireItem(UUID householdId, UUID itemId);

    /** 获取指定家庭下的活跃物品信息，不存在或非活跃则抛出异常。 */
    ItemInfo requireActiveItem(UUID householdId, UUID itemId);

    /** 获取指定家庭下的计量单位信息，不存在则抛出异常。 */
    UnitInfo requireUnit(UUID householdId, UUID unitId);

    /** 物品基本信息。 */
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

    /** 计量单位信息。 */
    record UnitInfo(
            UUID id,
            UUID householdId,
            String name,
            int decimalScale,
            String status
    ) {
    }
}

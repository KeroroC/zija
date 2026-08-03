package com.zija.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * 批量获取指定家庭下物品的名称。已归档但存在的物品返回名字；不属于该家庭的 id 不出现在结果中；
     * 返回 map 缺 key 表示找不到，由调用侧兜底。
     */
    Map<UUID, String> itemNames(UUID householdId, Collection<UUID> itemIds);

    /** 列出指定家庭下所有活跃物品（每日扫描等场景使用）。 */
    List<ItemInfo> listActiveItems(UUID householdId);

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
            String status,
            // 5a 新增：物品级提醒配置（INHERIT/DISABLED/CUSTOM + 天数 + 低库存）
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold
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

    /** 增量拉取家庭物品（含品牌、分类、单位、标签 join 后扁平化）。仅供 reporting 投影重建。 */
    ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit);

    record ItemDumpPage(List<ItemFlat> items, OffsetDateTime nextCursor, boolean hasMore) {}

    /** 物品扁平 DTO（仅供 dump）。 */
    record ItemFlat(
            UUID itemId,
            UUID householdId,
            String name,
            String managementType,
            String status,
            UUID categoryId,
            String categoryName,
            UUID brandId,
            String brandName,
            UUID unitId,
            String unitName,
            String tagNames,
            BigDecimal lowStockThreshold,
            String lowStockMode,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            OffsetDateTime updatedAt
    ) {}
}

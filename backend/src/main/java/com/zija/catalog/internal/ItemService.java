package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.*;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 物品（Item）管理服务，实现 {@link CatalogApi} 接口。
 * <p>
 * 负责家庭资产物品的完整生命周期管理，包括创建、归档、恢复和更新操作。
 * 支持物品与分类、品牌、单位、标签的关联，以及过期提醒和低库存预警配置。
 * 所有操作均限定在指定家庭（householdId）范围内，并通过乐观锁保证并发安全。
 */
@Service
class ItemService implements CatalogApi {

    private final ItemMapper itemMapper;
    private final UnitMapper unitMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final TagMapper tagMapper;
    private final SystemApi systemApi;

    ItemService(
            ItemMapper itemMapper,
            UnitMapper unitMapper,
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            TagMapper tagMapper,
            SystemApi systemApi
    ) {
        this.itemMapper = itemMapper;
        this.unitMapper = unitMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.tagMapper = tagMapper;
        this.systemApi = systemApi;
    }

    /**
     * 查询指定物品，不存在或不属于该家庭时抛出异常。
     */
    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.findByIdFull(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    /**
     * 查询指定物品且要求状态为 ACTIVE，归档物品会抛出异常。
     */
    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireActiveItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.findByIdFull(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitInfo requireUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return new UnitInfo(entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getDecimalScale(), entity.getStatus());
    }

    /**
     * 创建新物品，校验单位、分类、品牌、标签的有效性，并检查数量精度。
     */
    @Transactional
    public ItemEntity createItem(
            UUID householdId, String name, String managementType,
            UUID categoryId, UUID brandId, UUID unitId, String memo,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds
    ) {
        var unit = requireActiveUnit(householdId, unitId);
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
        }
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
        }
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
            }
        }
        if ("CUSTOM".equals(lowStockMode) && lowStockThreshold != null) {
            int scale = lowStockThreshold.stripTrailingZeros().scale();
            if (scale > unit.getDecimalScale()) {
                throw new CatalogUnitPrecisionInvalidException(scale, unit.getDecimalScale());
            }
        }

        var entity = new ItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name);
        entity.setManagementType(managementType);
        entity.setCategoryId(categoryId);
        entity.setBrandId(brandId);
        entity.setUnitId(unitId);
        entity.setMemo(memo);
        entity.setExpiryReminderMode(expiryReminderMode);
        entity.setExpiryReminderDays(expiryReminderDays);
        entity.setLowStockMode(lowStockMode);
        entity.setLowStockThreshold(lowStockThreshold);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        itemMapper.insert(entity);

        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                itemMapper.insertItemTag(householdId, entity.getId(), tagId);
            }
        }

        audit(householdId, "ITEM_CREATED", entity.getId());
        return entity;
    }

    /**
     * 归档物品，记录归档时间和操作人。
     */
    @Transactional
    public void archiveItem(UUID householdId, UUID id, UUID accountId, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus("ARCHIVED");
        entity.setArchivedAt(OffsetDateTime.now());
        entity.setArchivedBy(accountId);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "ITEM_ARCHIVED", id);
    }

    @Transactional
    public void restoreItem(UUID householdId, UUID id, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus("ACTIVE");
        entity.setArchivedAt(null);
        entity.setArchivedBy(null);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "ITEM_RESTORED", id);
    }

    @Transactional(readOnly = true)
    public ItemEntity findItem(UUID householdId, UUID id) {
        var entity = itemMapper.findByIdFull(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<UUID> findItemTagIds(UUID itemId) {
        return itemMapper.findTagIdsByItemId(itemId);
    }

    /**
     * 分页查询物品列表，支持按名称、管理类型、分类、品牌、标签、状态筛选和排序。
     */
    @Transactional(readOnly = true)
    public IPage<ItemEntity> listItems(
            UUID householdId, String q, String managementType,
            UUID categoryId, UUID brandId, UUID tagId, String status,
            int page, int pageSize, String sort
    ) {
        return itemMapper.findPage(
                new Page<>(page, pageSize), householdId,
                q, managementType, categoryId, brandId, tagId, status, resolveSort(sort));
    }

    private String resolveSort(String sort) {
        if (sort == null) return "updated_at DESC, id DESC";
        return switch (sort) {
            case "name" -> "name ASC, id ASC";
            case "-name" -> "name DESC, id DESC";
            default -> "updated_at DESC, id DESC";
        };
    }

    /**
     * 更新物品信息，支持部分更新（仅更新非 null 字段），使用乐观锁防止并发冲突。
     */
    @Transactional
    public ItemEntity updateItem(
            UUID householdId, UUID id,
            String name, UUID categoryId, UUID brandId, UUID unitId,
            String memo, UUID coverFileId,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds, Integer version
    ) {
        var entity = requireItemEntity(householdId, id);
        if (name != null) entity.setName(name.trim());
        if (memo != null) entity.setMemo(memo);
        if (coverFileId != null) entity.setCoverFileId(coverFileId);
        if (unitId != null) {
            requireActiveUnit(householdId, unitId);
            entity.setUnitId(unitId);
        }
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
            entity.setCategoryId(categoryId);
        }
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
            entity.setBrandId(brandId);
        }
        if (expiryReminderMode != null) {
            entity.setExpiryReminderMode(expiryReminderMode);
            entity.setExpiryReminderDays(expiryReminderDays);
        }
        if (lowStockMode != null) {
            if ("CUSTOM".equals(lowStockMode) && lowStockThreshold != null) {
                var unit = requireActiveUnit(householdId, entity.getUnitId());
                int scale = lowStockThreshold.stripTrailingZeros().scale();
                if (scale > unit.getDecimalScale()) {
                    throw new CatalogUnitPrecisionInvalidException(scale, unit.getDecimalScale());
                }
            }
            entity.setLowStockMode(lowStockMode);
            entity.setLowStockThreshold(lowStockThreshold);
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setVersion(version);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        if (tagIds != null) {
            itemMapper.deleteItemTags(id);
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
                itemMapper.insertItemTag(householdId, id, tagId);
            }
        }
        audit(householdId, "ITEM_UPDATED", id);
        return itemMapper.findByIdFull(id);
    }

    // --- Private helpers ---

    private ItemEntity requireItemEntity(UUID householdId, UUID id) {
        var entity = itemMapper.findByIdFull(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", id);
        }
        return entity;
    }

    private UnitEntity requireActiveUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return entity;
    }

    private <T> void requireActiveDictionary(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                              UUID id, UUID householdId, String type) {
        var entity = mapper.selectById(id);
        if (entity == null) {
            throw new CatalogArchivedDictionaryException(type, id);
        }
    }

    private ItemInfo toInfo(ItemEntity entity) {
        return new ItemInfo(
                entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getManagementType(), entity.getCategoryId(), entity.getBrandId(),
                entity.getUnitId(), entity.getCoverFileId(), entity.getStatus(),
                entity.getExpiryReminderMode(),
                entity.getExpiryReminderDays(),
                entity.getLowStockMode(),
                entity.getLowStockThreshold()
        );
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}

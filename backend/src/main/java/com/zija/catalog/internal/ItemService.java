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

    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.selectById(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireActiveItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.selectById(itemId);
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
        itemMapper.insert(entity);

        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                itemMapper.insertItemTag(householdId, entity.getId(), tagId);
            }
        }

        audit(householdId, "ITEM_CREATED", entity.getId());
        return entity;
    }

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
        var entity = itemMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<UUID> findItemTagIds(UUID itemId) {
        return itemMapper.findTagIdsByItemId(itemId);
    }

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
        return itemMapper.selectById(id);
    }

    // --- Private helpers ---

    private ItemEntity requireItemEntity(UUID householdId, UUID id) {
        var entity = itemMapper.selectById(id);
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
                entity.getUnitId(), entity.getCoverFileId(), entity.getStatus()
        );
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}

package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.internal.persistence.*;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 目录字典管理服务。
 * <p>
 * 负责管理物品目录的四类辅助字典数据：分类（Category）、品牌（Brand）、单位（Unit）和标签（Tag）。
 * 提供各字典的创建、更新、归档、恢复等 CRUD 操作，支持名称去重校验和 NFKC 规范化。
 * 分类支持树形结构，包含父子关系管理和循环引用检测。
 */
@Service
class CatalogDictionaryService {

    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final UnitMapper unitMapper;
    private final TagMapper tagMapper;
    private final ItemMapper itemMapper;
    private final SystemApi systemApi;

    CatalogDictionaryService(
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            UnitMapper unitMapper,
            TagMapper tagMapper,
            ItemMapper itemMapper,
            SystemApi systemApi
    ) {
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.unitMapper = unitMapper;
        this.tagMapper = tagMapper;
        this.itemMapper = itemMapper;
        this.systemApi = systemApi;
    }

    // --- Categories ---

    /**
     * 创建分类，支持父子层级关系，自动检查同级名称重复。
     */
    @Transactional
    public CategoryEntity createCategory(UUID householdId, String name, UUID parentId, int sortOrder) {
        String normalized = normalizeName(name);
        checkDuplicateCategory(householdId, parentId, normalized, null);
        var entity = new CategoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setParentId(parentId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        entity.setSortOrder(sortOrder);
        entity.setVersion(0);
        categoryMapper.insert(entity);
        audit(householdId, "CATEGORY_CREATED", entity.getId());
        return entity;
    }

    /**
     * 归档分类，要求该分类下无活跃子分类。
     */
    @Transactional
    public void archiveCategory(UUID householdId, UUID id, Integer version) {
        var entity = requireCategory(householdId, id);
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getParentId, id)
                .eq(CategoryEntity::getStatus, "ACTIVE"));
        if (childCount > 0) {
            throw new CatalogCategoryHasChildrenException();
        }
        entity.setStatus("ARCHIVED");
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_ARCHIVED", id);
    }

    @Transactional
    public void restoreCategory(UUID householdId, UUID id, Integer version) {
        var entity = requireCategory(householdId, id);
        entity.setStatus("ACTIVE");
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_RESTORED", id);
    }

    @Transactional
    public void updateCategory(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireCategory(householdId, id);
        String normalized = normalizeName(name);
        checkDuplicateCategory(householdId, entity.getParentId(), normalized, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_UPDATED", id);
    }

    /**
     * 移动分类到新的父节点下，包含循环引用检测。
     */
    @Transactional
    public void moveCategory(UUID householdId, UUID id, UUID newParentId, int newSortOrder, Integer version) {
        var entity = requireCategory(householdId, id);
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new CatalogCycleDetectedException();
            }
            var descendants = categoryMapper.findDescendantIds(id, householdId);
            if (descendants.contains(newParentId)) {
                throw new CatalogCycleDetectedException();
            }
        }
        entity.setParentId(newParentId);
        entity.setSortOrder(newSortOrder);
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_MOVED", id);
    }

    // --- Brands ---

    @Transactional
    public BrandEntity createBrand(UUID householdId, String name) {
        String normalized = normalizeName(name);
        checkDuplicateBrand(householdId, normalized, null);
        var entity = new BrandEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        brandMapper.insert(entity);
        audit(householdId, "BRAND_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public void archiveBrand(UUID householdId, UUID id, Integer version) {
        var entity = requireBrand(householdId, id);
        entity.setStatus("ARCHIVED");
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_ARCHIVED", id);
    }

    @Transactional
    public void restoreBrand(UUID householdId, UUID id, Integer version) {
        var entity = requireBrand(householdId, id);
        entity.setStatus("ACTIVE");
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_RESTORED", id);
    }

    @Transactional
    public void updateBrand(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireBrand(householdId, id);
        String normalized = normalizeName(name);
        checkDuplicateBrand(householdId, normalized, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_UPDATED", id);
    }

    // --- Units ---

    @Transactional
    public UnitEntity createUnit(UUID householdId, String name, int decimalScale) {
        if (decimalScale < 0 || decimalScale > 6) {
            throw new IllegalArgumentException("decimal_scale must be between 0 and 6");
        }
        String normalized = normalizeName(name);
        checkDuplicateUnit(householdId, normalized, null);
        var entity = new UnitEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setDecimalScale((short) decimalScale);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        unitMapper.insert(entity);
        audit(householdId, "UNIT_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public void updateUnit(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireUnit(householdId, id);
        String normalized = normalizeName(name);
        checkDuplicateUnit(householdId, normalized, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_UPDATED", id);
    }

    @Transactional
    public void archiveUnit(UUID householdId, UUID id, Integer version) {
        var entity = requireUnit(householdId, id);
        entity.setStatus("ARCHIVED");
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_ARCHIVED", id);
    }

    @Transactional
    public void restoreUnit(UUID householdId, UUID id, Integer version) {
        var entity = requireUnit(householdId, id);
        entity.setStatus("ACTIVE");
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_RESTORED", id);
    }

    /**
     * 修改单位的小数位数。
     * <p>
     * 增大时直接修改；缩小时，如果有物品引用该单位，返回影响范围供前端确认。
     * 确认后（confirmed=true），四舍五入截断所有受影响物品的 low_stock_threshold。
     */
    @Transactional
    public Map<String, Object> updateUnitDecimalScale(UUID householdId, UUID id, int newDecimalScale, Integer version, boolean confirmed) {
        if (newDecimalScale < 0 || newDecimalScale > 6) {
            throw new IllegalArgumentException("decimal_scale must be between 0 and 6");
        }
        var entity = requireUnit(householdId, id);
        int currentScale = entity.getDecimalScale();

        if (newDecimalScale == currentScale) {
            return Map.of("affectedItems", 0);
        }

        int affectedItems = 0;
        if (newDecimalScale < currentScale) {
            affectedItems = itemMapper.countByUnitId(id);
            if (affectedItems > 0 && !confirmed) {
                return Map.of(
                        "needsConfirmation", true,
                        "affectedItems", affectedItems,
                        "currentScale", currentScale,
                        "newScale", newDecimalScale
                );
            }
            if (affectedItems > 0) {
                itemMapper.truncateLowStockThreshold(id, newDecimalScale);
            }
        }

        entity.setDecimalScale((short) newDecimalScale);
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_DECIMAL_SCALE_UPDATED", id);
        return Map.of("affectedItems", affectedItems);
    }

    // --- Tags ---

    @Transactional
    public TagEntity createTag(UUID householdId, String name) {
        String normalized = normalizeName(name);
        checkDuplicateTag(householdId, normalized, null);
        var entity = new TagEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        tagMapper.insert(entity);
        audit(householdId, "TAG_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public void archiveTag(UUID householdId, UUID id, Integer version) {
        var entity = requireTag(householdId, id);
        entity.setStatus("ARCHIVED");
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_ARCHIVED", id);
    }

    @Transactional
    public void updateTag(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireTag(householdId, id);
        String normalized = normalizeName(name);
        checkDuplicateTag(householdId, normalized, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_UPDATED", id);
    }

    @Transactional
    public void restoreTag(UUID householdId, UUID id, Integer version) {
        var entity = requireTag(householdId, id);
        entity.setStatus("ACTIVE");
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_RESTORED", id);
    }

    // --- Query ---

    /**
     * 查询分类树，可选是否包含已归档分类，按排序序号升序返回。
     */
    @Transactional(readOnly = true)
    public List<CategoryEntity> findCategoryTree(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(CategoryEntity::getStatus, "ACTIVE");
        }
        wrapper.orderByAsc(CategoryEntity::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<BrandEntity> findBrands(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<BrandEntity>()
                .eq(BrandEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(BrandEntity::getStatus, "ACTIVE");
        }
        return brandMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<UnitEntity> findUnits(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<UnitEntity>()
                .eq(UnitEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(UnitEntity::getStatus, "ACTIVE");
        }
        return unitMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<TagEntity> findTags(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(TagEntity::getStatus, "ACTIVE");
        }
        return tagMapper.selectList(wrapper);
    }

    // --- Helpers ---

    private CategoryEntity requireCategory(UUID householdId, UUID id) {
        var entity = categoryMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("category not found");
        }
        return entity;
    }

    private BrandEntity requireBrand(UUID householdId, UUID id) {
        var entity = brandMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("brand not found");
        }
        return entity;
    }

    private UnitEntity requireUnit(UUID householdId, UUID id) {
        var entity = unitMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("unit not found");
        }
        return entity;
    }

    private TagEntity requireTag(UUID householdId, UUID id) {
        var entity = tagMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("tag not found");
        }
        return entity;
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String nfkc = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    private void checkDuplicateCategory(UUID householdId, UUID parentId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getHouseholdId, householdId)
                .eq(CategoryEntity::getParentId, parentId)
                .eq(CategoryEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(CategoryEntity::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateBrand(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<BrandEntity>()
                .eq(BrandEntity::getHouseholdId, householdId)
                .eq(BrandEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(BrandEntity::getId, excludeId);
        }
        if (brandMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateUnit(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<UnitEntity>()
                .eq(UnitEntity::getHouseholdId, householdId)
                .eq(UnitEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(UnitEntity::getId, excludeId);
        }
        if (unitMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateTag(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getHouseholdId, householdId)
                .eq(TagEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(TagEntity::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}

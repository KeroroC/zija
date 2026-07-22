package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.internal.persistence.*;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
class CatalogDictionaryService {

    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final UnitMapper unitMapper;
    private final TagMapper tagMapper;
    private final SystemApi systemApi;

    CatalogDictionaryService(
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            UnitMapper unitMapper,
            TagMapper tagMapper,
            SystemApi systemApi
    ) {
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.unitMapper = unitMapper;
        this.tagMapper = tagMapper;
        this.systemApi = systemApi;
    }

    // --- Categories ---

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
        categoryMapper.insert(entity);
        audit(householdId, "CATEGORY_CREATED", entity.getId());
        return entity;
    }

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
        unitMapper.insert(entity);
        audit(householdId, "UNIT_CREATED", entity.getId());
        return entity;
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

    // --- Query ---

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

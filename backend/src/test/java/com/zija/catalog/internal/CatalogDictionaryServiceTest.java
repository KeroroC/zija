package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.internal.event.CatalogEventPublisher;
import com.zija.catalog.internal.exception.CatalogCategoryHasChildrenException;
import com.zija.catalog.internal.exception.CatalogDictionaryNameExistsException;
import com.zija.catalog.internal.persistence.*;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogDictionaryServiceTest {

    private CategoryMapper categoryMapper;
    private BrandMapper brandMapper;
    private UnitMapper unitMapper;
    private TagMapper tagMapper;
    private ItemMapper itemMapper;
    private SystemApi systemApi;
    private CatalogEventPublisher eventPublisher;
    private CatalogDictionaryService service;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        brandMapper = mock(BrandMapper.class);
        unitMapper = mock(UnitMapper.class);
        tagMapper = mock(TagMapper.class);
        itemMapper = mock(ItemMapper.class);
        systemApi = mock(SystemApi.class);
        eventPublisher = mock(CatalogEventPublisher.class);
        service = new CatalogDictionaryService(categoryMapper, brandMapper, unitMapper, tagMapper, itemMapper, systemApi, eventPublisher);
    }

    @Test
    void createCategoryNormalizesName() {
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var entity = service.createCategory(householdId, "  家电  ", null, 0);

        var captor = org.mockito.ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryMapper).insert(captor.capture());
        var inserted = captor.getValue();

        assertThat(inserted.getName()).isEqualTo("家电");
        assertThat(inserted.getNameNormalized()).isEqualTo("家电");
        assertThat(inserted.getHouseholdId()).isEqualTo(householdId);
        assertThat(inserted.getParentId()).isNull();
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createCategoryRejectsDuplicateName() {
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.createCategory(householdId, "家电", null, 0))
                .isInstanceOf(CatalogDictionaryNameExistsException.class);
    }

    @Test
    void archiveCategoryRejectsWhenActiveChildrenExist() {
        UUID categoryId = UUID.randomUUID();
        var entity = new CategoryEntity();
        entity.setId(categoryId);
        entity.setHouseholdId(householdId);
        entity.setStatus("ACTIVE");
        when(categoryMapper.selectById(categoryId)).thenReturn(entity);
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.archiveCategory(householdId, categoryId, 0))
                .isInstanceOf(CatalogCategoryHasChildrenException.class);

        verify(categoryMapper, never()).updateById(any(CategoryEntity.class));
    }

    @Test
    void createBrandNormalizesName() {
        when(brandMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var entity = service.createBrand(householdId, "  Sony  ");

        var captor = org.mockito.ArgumentCaptor.forClass(BrandEntity.class);
        verify(brandMapper).insert(captor.capture());
        var inserted = captor.getValue();

        assertThat(inserted.getName()).isEqualTo("Sony");
        assertThat(inserted.getNameNormalized()).isEqualTo("sony");
        assertThat(inserted.getHouseholdId()).isEqualTo(householdId);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createUnitRejectsDecimalScaleOutOfRange() {
        assertThatThrownBy(() -> service.createUnit(householdId, "个", -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.createUnit(householdId, "个", 7))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(unitMapper);
    }

    @Test
    void createUnitRejectsDuplicateName() {
        when(unitMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.createUnit(householdId, "个", 0))
                .isInstanceOf(CatalogDictionaryNameExistsException.class);
    }
}

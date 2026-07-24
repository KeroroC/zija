package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemMapper extends BaseMapper<ItemEntity> {

    void insertItemTag(@Param("householdId") UUID householdId,
                       @Param("itemId") UUID itemId,
                       @Param("tagId") UUID tagId);

    void deleteItemTags(@Param("itemId") UUID itemId);

    List<UUID> findTagIdsByItemId(@Param("itemId") UUID itemId);

    IPage<ItemEntity> findPage(
            Page<ItemEntity> page,
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("managementType") String managementType,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("tagId") UUID tagId,
            @Param("status") String status,
            @Param("orderBy") String orderBy
    );

    int countByUnitId(@Param("unitId") UUID unitId);

    int truncateLowStockThreshold(@Param("unitId") UUID unitId, @Param("newScale") int newScale);
}

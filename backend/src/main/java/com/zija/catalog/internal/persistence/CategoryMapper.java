package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {

    List<CategoryEntity> findTree(@Param("householdId") UUID householdId);

    List<UUID> findDescendantIds(@Param("categoryId") UUID categoryId, @Param("householdId") UUID householdId);

    List<CategoryEntity> findAncestors(@Param("categoryId") UUID categoryId, @Param("householdId") UUID householdId);
}

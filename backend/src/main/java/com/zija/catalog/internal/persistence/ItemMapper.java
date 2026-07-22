package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}

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

    ItemEntity findByIdFull(@Param("id") UUID id);

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

    /**
     * 附件离开物品（删除进回收站 / 改挂到别处）后清理封面指定：仅当当前封面正是该附件时清除。
     * 同步递增版本号，保护并发封面操作（冲突方收到 409）。
     *
     * @return 受影响行数
     */
    int clearCoverIfCurrent(@Param("householdId") UUID householdId,
                            @Param("itemId") UUID itemId,
                            @Param("fileId") UUID fileId);
}

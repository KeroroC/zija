package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StockPositionMapper extends BaseMapper<StockPositionEntity> {

    /** 锁定单个库存位行（不存在返回 null）。事务内调用。 */
    StockPositionEntity lockOne(@Param("householdId") UUID householdId,
                                @Param("lotId") UUID lotId,
                                @Param("locationId") UUID locationId);

    /** 条件加增：quantity = quantity + ?, revision = revision + 1，WHERE 依 household/lot/location。 */
    int addQuantity(@Param("householdId") UUID householdId,
                    @Param("lotId") UUID lotId,
                    @Param("locationId") UUID locationId,
                    @Param("delta") BigDecimal delta);

    /** 条件扣减：仅当 quantity - delta >= 0 时更新，并 revision+1；返回受影响行数（0 表示不足）。 */
    int subtractIfSufficient(@Param("householdId") UUID householdId,
                             @Param("lotId") UUID lotId,
                             @Param("locationId") UUID locationId,
                             @Param("delta") BigDecimal delta);

    IPage<StockPositionEntity> findPage(Page<StockPositionEntity> page,
                                         @Param("householdId") UUID householdId,
                                         @Param("itemId") UUID itemId,
                                         @Param("locationId") UUID locationId,
                                         @Param("orderBy") String orderBy);
}

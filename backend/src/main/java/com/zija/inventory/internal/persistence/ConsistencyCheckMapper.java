package com.zija.inventory.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ConsistencyCheckMapper {

    /** 当前库存位集合（household 或 item 过滤）。 */
    List<StockPositionEntity> currentPositions(@Param("householdId") UUID householdId,
                                               @Param("itemId") UUID itemId);

    /** 按库存位签名汇总流水应有数量。 */
    List<StockPositionEntity> expectedFromMovements(@Param("householdId") UUID householdId,
                                                    @Param("itemId") UUID itemId);
}

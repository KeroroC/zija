package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface StockFlatMapper extends BaseMapper<StockFlatEntity> {
    int upsert(StockFlatEntity entity);
    int deleteByLot(@Param("householdId") UUID householdId, @Param("lotId") UUID lotId);
}

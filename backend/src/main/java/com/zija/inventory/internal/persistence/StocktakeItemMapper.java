package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface StocktakeItemMapper extends BaseMapper<StocktakeItemEntity> {

    /** 按盘点单 id 锁定所有行项（SELECT ... FOR UPDATE）。 */
    List<StocktakeItemEntity> lockByStocktake(@Param("householdId") UUID householdId,
                                              @Param("stocktakeId") UUID stocktakeId);

    /** 删除指定盘点单的所有行项。 */
    int deleteByStocktake(@Param("stocktakeId") UUID stocktakeId);

    /** 批量插入盘点行项。 */
    int batchInsert(@Param("items") List<StocktakeItemEntity> items);
}

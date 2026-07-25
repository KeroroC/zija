package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface StocktakeMapper extends BaseMapper<StocktakeEntity> {

    /** 按 household + id 锁定盘点单行（SELECT ... FOR UPDATE）。 */
    StocktakeEntity lockById(@Param("householdId") UUID householdId, @Param("id") UUID id);

    /** 分页查询盘点单，可选按状态过滤，支持动态排序。 */
    IPage<StocktakeEntity> findPage(Page<StocktakeEntity> page,
                                    @Param("householdId") UUID householdId,
                                    @Param("status") String status,
                                    @Param("orderBy") String orderBy);
}

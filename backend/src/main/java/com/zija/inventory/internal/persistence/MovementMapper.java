package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MovementMapper extends BaseMapper<MovementEntity> {

    /** 查询某流水的反向流水是否存在（冲正检查）。 */
    int countReversalOf(@Param("householdId") UUID householdId, @Param("originalId") UUID originalId);

    /** 按 (household, lot) 聚合签名（健壮重建用）。 */
    List<MovementEntity> findByLot(@Param("householdId") UUID householdId, @Param("lotId") UUID lotId);

    IPage<MovementEntity> findPage(Page<MovementEntity> page,
                                   @Param("householdId") UUID householdId,
                                   @Param("type") String type,
                                   @Param("itemId") UUID itemId,
                                   @Param("locationId") UUID locationId,
                                   @Param("operatorAccountId") UUID operatorAccountId,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   @Param("orderBy") String orderBy);
}

package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {

    /** 锁定幂等记录行（不存在返回 null）。事务内争用唯一约束。 */
    IdempotencyRecordEntity lockByKey(@Param("householdId") UUID householdId,
                                      @Param("key") String key);
}

package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.UUID;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {

    /** 锁定幂等记录行（不存在返回 null）。事务内争用唯一约束。 */
    IdempotencyRecordEntity lockByKey(@Param("householdId") UUID householdId,
                                      @Param("key") String key);

    /** INSERT … ON CONFLICT DO NOTHING，返回受影响行数（0 表示已存在）。不会导致事务中止。 */
    int insertIgnore(@Param("entity") IdempotencyRecordEntity entity);

    /** 更新已声明的幂等记录的 movement_id 和 response_payload。 */
    int updateResult(@Param("householdId") UUID householdId,
                     @Param("key") String key,
                     @Param("movementId") UUID movementId,
                     @Param("responsePayload") Map<String, Object> responsePayload);
}

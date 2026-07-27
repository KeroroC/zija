package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DeadLetterMapper extends BaseMapper<DeadLetterEntity> {
    List<DeadLetterEntity> findDueForRetry(@Param("now") OffsetDateTime now, @Param("limit") int limit);
    int incrementFailure(@Param("id") UUID id, @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                          @Param("lastError") String lastError);
    int markAbandoned(@Param("id") UUID id);
}

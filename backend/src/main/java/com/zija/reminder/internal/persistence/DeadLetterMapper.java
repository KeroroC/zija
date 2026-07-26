package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DeadLetterMapper extends BaseMapper<DeadLetterEntity> {
    /** 列出 next_retry_at<=now 且 abandoned=false 的行（FOR UPDATE SKIP LOCKED 避免重投竞争）。 */
    List<DeadLetterEntity> findDueForRetry(@Param("now") OffsetDateTime now, @Param("limit") int limit);
    int incrementFailure(@Param("id") UUID id, @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                         @Param("lastError") String lastError);
    int markAbandoned(@Param("id") UUID id);
}

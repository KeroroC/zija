package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface ReportingProcessedEventMapper extends BaseMapper<ProcessedEventEntity> {
    /** INSERT ON CONFLICT DO NOTHING，返回受影响行数（0=已存在跳过）。 */
    int insertOnConflictDoNothing(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}

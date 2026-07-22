package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.system.internal.AuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    void insert(AuditEvent event);

    List<AuditLogEntity> findByActor(@Param("actorAccountId") UUID actorAccountId);

    IPage<AuditLogEntity> queryByCondition(
            Page<AuditLogEntity> page,
            @Param("householdId") UUID householdId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("action") String action,
            @Param("actorAccountId") UUID actorAccountId,
            @Param("outcome") String outcome
    );
}

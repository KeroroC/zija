package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zija.system.internal.AuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    void insert(AuditEvent event);

    List<AuditLogEntity> findByActor(@Param("actorAccountId") UUID actorAccountId);
}

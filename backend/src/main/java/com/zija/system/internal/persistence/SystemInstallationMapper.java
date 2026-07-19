package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;

@Mapper
public interface SystemInstallationMapper
        extends BaseMapper<SystemInstallationEntity> {

    OffsetDateTime selectDatabaseTime();
}

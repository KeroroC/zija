package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {

    int incrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);

    int decrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);
}

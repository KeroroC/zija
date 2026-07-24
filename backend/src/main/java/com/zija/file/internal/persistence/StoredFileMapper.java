package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {

    int incrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);

    int decrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);

    /**
     * 原子递减引用计数（仅当 reference_count > 0 时）。
     *
     * @return 递减后的实体；若引用计数已为 0 或文件不存在则返回 null
     */
    StoredFileEntity decrementReferenceCountIfPositive(@Param("id") UUID id, @Param("householdId") UUID householdId);
}

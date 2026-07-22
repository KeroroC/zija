package com.zija.location.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationMapper extends BaseMapper<LocationEntity> {

    List<LocationEntity> findTree(@Param("householdId") UUID householdId);

    List<UUID> findDescendantIds(@Param("locationId") UUID locationId, @Param("householdId") UUID householdId);

    int markReferenced(@Param("id") UUID id, @Param("householdId") UUID householdId);
}

package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HouseholdMapper extends BaseMapper<HouseholdEntity> {
    int insertSingleton(HouseholdEntity entity);
}

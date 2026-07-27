package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MovementFlatMapper extends BaseMapper<MovementFlatEntity> {
    int upsert(MovementFlatEntity entity);
}

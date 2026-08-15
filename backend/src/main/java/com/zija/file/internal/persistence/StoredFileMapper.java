package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {

    /** 回收站中删除时间早于截止时刻的附件（进入物理清除范围）。 */
    List<StoredFileEntity> findExpired(@Param("before") OffsetDateTime before);
}

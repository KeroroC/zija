package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {

    /** 仅恢复仍在回收站中的行，避免与永久删除并发时写回已删除实体。 */
    @Update("""
            UPDATE stored_file
            SET deleted_at = NULL
            WHERE id = #{fileId}
              AND household_id = #{householdId}
              AND deleted_at IS NOT NULL
            """)
    int restoreIfRecycled(
            @Param("householdId") UUID householdId,
            @Param("fileId") UUID fileId
    );

    /** 回收站中删除时间早于截止时刻的附件（进入物理清除范围）。 */
    List<StoredFileEntity> findExpired(@Param("before") OffsetDateTime before);
}

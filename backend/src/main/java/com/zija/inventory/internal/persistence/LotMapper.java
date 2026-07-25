package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LotMapper extends BaseMapper<LotEntity> {

    /** 按给定批次 id 集合按 UUID 顺序加锁。 */
    void lockByIds(@Param("householdId") UUID householdId, @Param("ids") List<UUID> ids);

    /** 检测同一物品下序列号是否已存在（用于重复警告，不阻止）。 */
    int countByItemAndSerial(@Param("householdId") UUID householdId,
                             @Param("itemId") UUID itemId,
                             @Param("serialNumber") String serialNumber);

    /** 分页查询批次，包含物品名称、单位和总数量。 */
    IPage<LotWithDetails> findPage(Page<LotWithDetails> page,
                                   @Param("householdId") UUID householdId,
                                   @Param("itemId") UUID itemId);
}

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

    /** 查询指定 household 指定日期的批次号最大序号（用于自动生成）。 */
    Integer selectMaxSeqForDate(@Param("householdId") UUID householdId,
                                @Param("date") java.time.LocalDate date);

    /**
     * 取该日期的进程内事务级 advisory 锁，把"读 MAX + 写 lot_number"这段临界区
     * 串行化，避免无锁生成导致的 UNIQUE(lot_number) 冲突。
     * <p>
     * 日期相同 → 同一 key → 互相阻塞；日期不同 → 不同 key → 完全并行。
     * 锁随事务结束（commit/rollback）自动释放，不需手动解锁。
     */
    boolean acquireDailyLotSeqLock(@Param("dateKey") long dateKey);

    /** 分页查询批次，包含物品名称、单位和总数量。 */
    IPage<LotWithDetails> findPage(Page<LotWithDetails> page,
                                   @Param("householdId") UUID householdId,
                                   @Param("itemId") UUID itemId);
}

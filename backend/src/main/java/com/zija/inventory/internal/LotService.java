package com.zija.inventory.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.LotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class LotService {

    private final LotMapper lotMapper;
    private final CatalogApi catalogApi;

    public LotService(LotMapper lotMapper, CatalogApi catalogApi) {
        this.lotMapper = lotMapper;
        this.catalogApi = catalogApi;
    }

    /**
     * 新建批次。校验物品必须为活跃状态。
     *
     * @return 新建批次的 ID
     * @throws InventoryArchivedItemException 物品已归档
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID createLot(UUID householdId, UUID itemId, LocalDate purchaseDate,
                          LocalDate productionDate, LocalDate expiryDate,
                          String lotNumber, String serialNumber, String memo) {
        // Validate item is active (throws InventoryArchivedItemException if archived)
        catalogApi.requireActiveItem(householdId, itemId);

        var entity = new LotEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setItemId(itemId);
        entity.setPurchaseDate(purchaseDate);
        entity.setProductionDate(productionDate);
        entity.setExpiryDate(expiryDate);
        entity.setLotNumber(lotNumber);
        entity.setSerialNumber(serialNumber);
        entity.setMemo(memo);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setVersion(0);

        lotMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 修正批次资料。使用乐观锁（{@code @Version}）防止并发修改。
     * <p>
     * {@code item_id} 不可修改：本方法只更新允许的字段。
     *
     * @param clientVersion 客户端持有的版本号，与数据库不匹配时抛出冲突异常
     * @return 更新后的批次实体（version + 1）
     * @throws InventoryLotNotFoundException      批次不存在
     * @throws InventoryLotVersionConflictException 版本冲突
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LotEntity updateLotMeta(UUID householdId, UUID lotId, int clientVersion,
                                   LocalDate purchaseDate, LocalDate productionDate,
                                   LocalDate expiryDate, String lotNumber,
                                   String serialNumber, String memo) {
        var lot = requireLot(householdId, lotId);

        lot.setPurchaseDate(purchaseDate);
        lot.setProductionDate(productionDate);
        lot.setExpiryDate(expiryDate);
        lot.setLotNumber(lotNumber);
        lot.setSerialNumber(serialNumber);
        lot.setMemo(memo);
        lot.setUpdatedAt(OffsetDateTime.now());

        // MyBatis-Plus @Version: WHERE version = <current>, SET version = version + 1
        int rows = lotMapper.updateById(lot);
        if (rows == 0) {
            throw new InventoryLotVersionConflictException();
        }

        return lotMapper.selectById(lotId);
    }

    /**
     * 获取批次，不存在或家庭不匹配时抛出异常。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LotEntity requireLot(UUID householdId, UUID lotId) {
        var lot = lotMapper.selectById(lotId);
        if (lot == null || !lot.getHouseholdId().equals(householdId)) {
            throw new InventoryLotNotFoundException();
        }
        return lot;
    }

    /**
     * 检测同一物品下序列号是否已存在（仅警告，不阻止）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean serialNumberDuplicated(UUID householdId, UUID itemId, String serialNumber) {
        return lotMapper.countByItemAndSerial(householdId, itemId, serialNumber) > 0;
    }
}

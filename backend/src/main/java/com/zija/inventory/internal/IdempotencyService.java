package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.IdempotencyRecordEntity;
import com.zija.inventory.internal.persistence.IdempotencyRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 幂等服务，用于管理库存命令的幂等键。
 * <p>
 * 所有方法均在调用方事务内执行（{@code Propagation.MANDATORY}）。
 * <ul>
 *   <li>{@link #lockOrFind} — 先以 INSERT … ON CONFLICT DO NOTHING 声明幂等键（claim），
 *       若插入成功则为首调用方，返回空让调用方继续执行命令；
 *       若已存在则 SELECT … FOR UPDATE 阻塞等待首调用方提交后回放结果，
 *       哈希不匹配则抛出冲突异常。</li>
 *   <li>{@link #recordSuccess} — 命令成功后登记结果。先尝试 INSERT … ON CONFLICT DO NOTHING，
 *       若记录已由 {@link #lockOrFind} 声明则改用 UPDATE 写入 movement_id 和 response_payload。</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordMapper mapper;

    public IdempotencyService(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在调用方事务内执行。
     * <p>
     * 先以 {@code INSERT … ON CONFLICT DO NOTHING} 声明幂等键（claim）。
     * 若插入成功（affected=1），说明当前线程为首调用方，返回空让调用方继续执行命令。
     * 若插入未成功（affected=0），说明另一事务已声明或已完成该键，
     * 此时以 {@code SELECT … FOR UPDATE} 锁定该行——若另一事务尚未提交则阻塞等待，
     * 等待结束后检查哈希并返回命中记录供调用方回放。
     * <p>
     * 若另一事务回滚导致记录消失，则重试声明（最多 2 次）。
     *
     * @param householdId 家庭 ID
     * @param key         幂等键
     * @param requestHash 请求哈希（由 {@link RequestHashing#sha256} 生成）
     * @return 命中记录（哈希匹配时），或空（首次调用时）
     * @throws InventoryIdempotencyConflictException 同一幂等键但请求哈希不匹配时
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecordEntity> lockOrFind(UUID householdId, String key, String requestHash) {
        // 最多重试 2 次：首次声明 + 一次因对方回滚而重试
        for (int attempt = 0; attempt < 2; attempt++) {
            // 1. 尝试声明幂等键
            var claim = new IdempotencyRecordEntity();
            claim.setId(UUID.randomUUID());
            claim.setHouseholdId(householdId);
            claim.setIdempotencyKey(key);
            claim.setRequestHash(requestHash);
            claim.setMovementId(null);
            claim.setResponsePayload(null);
            claim.setCreatedAt(OffsetDateTime.now());

            int inserted = mapper.insertIgnore(claim);
            if (inserted == 1) {
                // 声明成功——当前线程为首调用方，继续执行命令
                return Optional.empty();
            }

            // 2. 键已存在，锁定并检查
            var existing = mapper.lockByKey(householdId, key);
            if (existing != null) {
                if (!requestHash.equals(existing.getRequestHash())) {
                    throw new InventoryIdempotencyConflictException();
                }
                return Optional.of(existing);
            }

            // 3. 记录消失（对方回滚），重试声明
        }
        // 极端情况：两次声明均因对方回滚而失败，返回空让调用方继续
        return Optional.empty();
    }

    /**
     * 命令成功后登记结果。
     * <p>
     * 先以 {@code INSERT … ON CONFLICT DO NOTHING} 创建记录（兼容直接调用场景），
     * 若记录已存在（affected=0，由 {@link #lockOrFind} 声明）则改用 UPDATE 写入 movement_id 和 response_payload。
     *
     * @param householdId    家庭 ID
     * @param key            幂等键
     * @param requestHash    请求哈希
     * @param movementId     库存流水 ID
     * @param responsePayload 响应载荷
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(UUID householdId, String key, String requestHash,
                              UUID movementId, Map<String, Object> responsePayload) {
        var e = new IdempotencyRecordEntity();
        e.setId(UUID.randomUUID());
        e.setHouseholdId(householdId);
        e.setIdempotencyKey(key);
        e.setRequestHash(requestHash);
        e.setMovementId(movementId);
        e.setResponsePayload(responsePayload);
        e.setCreatedAt(OffsetDateTime.now());

        int inserted = mapper.insertIgnore(e);
        if (inserted == 0) {
            // 记录已由 lockOrFind 声明（或先前调用），UPDATE 结果
            mapper.updateResult(householdId, key, movementId, responsePayload);
        }
    }
}

package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.IdempotencyRecordEntity;
import com.zija.inventory.internal.persistence.IdempotencyRecordMapper;
import org.springframework.dao.DuplicateKeyException;
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
 *   <li>{@link #lockOrFind} — 锁定幂等记录行，若已存在且哈希匹配则返回命中记录，哈希不匹配则抛出冲突异常，不存在则返回空。</li>
 *   <li>{@link #recordSuccess} — 命令成功后登记结果，并发争用时捕获 {@link DuplicateKeyException} 并比对哈希。</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordMapper mapper;

    public IdempotencyService(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在调用方事务内执行。返回命中记录则调用方跳过命令并回放 responsePayload；
     * 返回 {@link Optional#empty()} 则继续执行命令并在成功后由调用方记录。
     *
     * @param householdId 家庭 ID
     * @param key         幂等键
     * @param requestHash 请求哈希（由 {@link RequestHashing#sha256} 生成）
     * @return 命中记录（哈希匹配时），或空（首次调用时）
     * @throws InventoryIdempotencyConflictException 同一幂等键但请求哈希不匹配时
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecordEntity> lockOrFind(UUID householdId, String key, String requestHash) {
        var existing = mapper.lockByKey(householdId, key);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new InventoryIdempotencyConflictException();
            }
            return Optional.of(existing);
        }
        return Optional.empty();
    }

    /**
     * 命令成功后登记结果。
     * <p>
     * 若并发争用导致 {@link DuplicateKeyException}，则按已写入记录比对哈希，
     * 哈希不匹配时抛出冲突异常。
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
        try {
            mapper.insert(e);
        } catch (DuplicateKeyException dup) {
            // 并发争用：另一线程先写入，按其记录比对 hash 判断
            var r = mapper.lockByKey(householdId, key);
            if (r != null && !requestHash.equals(r.getRequestHash())) {
                throw new InventoryIdempotencyConflictException();
            }
        }
    }
}

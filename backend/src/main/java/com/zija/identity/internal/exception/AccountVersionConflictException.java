package com.zija.identity.internal.exception;

/**
 * 账户记录的乐观锁版本冲突。
 *
 * <p>表示账户记录在读取后、被本次事务写回之前已被其他事务修改并提升
 * {@code version}，导致 {@code UPDATE ... WHERE version = ?} 影响 0 行。
 * 客户端应重新读取最新数据后再次提交。</p>
 */
public class AccountVersionConflictException extends RuntimeException {
    public AccountVersionConflictException() {
        super("account version conflict");
    }
}
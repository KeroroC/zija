package com.zija.reminder.internal;

import org.springframework.dao.DuplicateKeyException;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 惰性初始化工具：按 household 单例初始化时，不存在则创建，
 * 并发创建撞唯一键（DuplicateKeyException）时重读并返回已有行。
 */
public final class LazyInit {

    private LazyInit() {}

    public static <E> E getOrCreate(Supplier<E> finder, Supplier<E> factory, Consumer<E> inserter) {
        E existing = finder.get();
        if (existing != null) return existing;
        E entity = factory.get();
        try {
            inserter.accept(entity);
        } catch (DuplicateKeyException dup) {
            return finder.get();
        }
        return entity;
    }
}

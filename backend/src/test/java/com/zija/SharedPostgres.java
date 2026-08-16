package com.zija;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JVM 级单例 PostgreSQL 容器。
 *
 * <p>所有集成测试共享同一个容器实例，避免每个测试类各自拉起独立 Postgres + 跑 Flyway，
 * 将容器启动开销从 N 次降为 1 次。
 *
 * <p>使用方式：在测试类中声明
 * <pre>{@code
 * @DynamicPropertySource
 * static void pgProps(DynamicPropertyRegistry r) {
 *     r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
 *     r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
 *     r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
 * }
 * }</pre>
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    static {
        INSTANCE.start();
    }

    private SharedPostgres() {
    }

    public static PostgreSQLContainer<?> get() {
        return INSTANCE;
    }
}

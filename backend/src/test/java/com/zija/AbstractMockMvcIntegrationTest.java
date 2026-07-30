package com.zija;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 共享基类：MockMvc/Web 集成测试用真实 Postgres（Testcontainers）+ ServiceConnection，
 * 满足 Spring Modulith 事件发布在上下文刷新阶段连接 DataSource 的需求，
 * 同时启用 Flyway 建表，让 reminder/reporting 的定时任务（dead-letter 重投等）有表可查。
 *
 * <p>继承方只需在子类上声明 {@code @AutoConfigureMockMvc} 并 mock 业务依赖，
 * 不必关心数据库或 Modulith 自动配置。
 *
 * <p>容器使用 {@link SharedPostgres} JVM 级单例，所有测试类共享同一个 Postgres 实例。
 */
@SpringBootTest(properties = {
        "zija.session.jdbc.enabled=false"
})
public abstract class AbstractMockMvcIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }
}
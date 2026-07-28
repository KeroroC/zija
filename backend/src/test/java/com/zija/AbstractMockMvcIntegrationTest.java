package com.zija;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 共享基类：MockMvc/Web 集成测试用真实 Postgres（Testcontainers）+ ServiceConnection，
 * 满足 Spring Modulith 事件发布在上下文刷新阶段连接 DataSource 的需求，
 * 同时启用 Flyway 建表，让 reminder/reporting 的定时任务（dead-letter 重投等）有表可查。
 *
 * <p>继承方只需在子类上声明 {@code @AutoConfigureMockMvc} 并 mock 业务依赖，
 * 不必关心数据库或 Modulith 自动配置。
 */
@SpringBootTest(properties = {
        "zija.session.jdbc.enabled=false"
})
@Testcontainers
public abstract class AbstractMockMvcIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
}
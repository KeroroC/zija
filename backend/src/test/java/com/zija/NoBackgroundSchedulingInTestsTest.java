package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归测试：集成测试上下文中不得注册任何 {@code com.zija} 的 {@code @Scheduled} 后台任务。
 *
 * <p>背景：所有集成测试共用 {@link SharedPostgres} 单例容器，且 Spring 上下文全程缓存
 * （无 {@code @DirtiesContext}）。若后台调度开启，各缓存上下文的 dead-letter 重投服务
 * 会持续写库；而 30+ 个测试类各自用不同表清单/顺序执行 {@code TRUNCATE}。
 * 重投事务先写 audit_log/catalog_item（持 RowExclusiveLock）再做 FK 校验取
 * household/account 的 RowShareLock，与 TRUNCATE 已持有的 AccessExclusiveLock
 * 形成锁顺序反转 → {@code ERROR: deadlock detected}，CI 随机失败。
 *
 * <p>关闭方式是把各任务的 cron 属性设为 {@code "-"}
 * （{@code ScheduledTaskRegistrar.CRON_DISABLED}），见 {@code src/test/resources/application.properties}。
 * 注意不能靠删除 {@code ReminderSchedulingConfig} 的 {@code @EnableScheduling}——
 * Spring Modulith 的 {@code MomentsAutoConfiguration} 同样带 {@code @EnableScheduling}。
 *
 * <p>Modulith 自带的 {@code Moments.everyHour/everyMidnight} 不在断言范围内：
 * 本项目没有任何 Moments 事件监听器，它们只发布事件、不写库。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class NoBackgroundSchedulingInTestsTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired ApplicationContext context;

    @Test
    void noApplicationScheduledTaskIsRegistered() {
        List<String> zijaTasks = context.getBeansOfType(ScheduledTaskHolder.class)
                .values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(NoBackgroundSchedulingInTestsTest::targetClassName)
                .filter(name -> name.startsWith("com.zija."))
                .toList();

        assertThat(zijaTasks)
                .as("测试环境必须禁用应用的 @Scheduled 任务：后台写库会与各测试类的 TRUNCATE 争锁造成死锁")
                .isEmpty();
    }

    private static String targetClassName(ScheduledTask task) {
        if (task.getTask().getRunnable() instanceof ScheduledMethodRunnable runnable) {
            return runnable.getMethod().getDeclaringClass().getName();
        }
        return task.toString();
    }
}

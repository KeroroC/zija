package com.zija;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@code logback-spring.xml} 的 prod 分支：写入 {@code zija.log.path} 下的滚动文件，
 * 且 {@code error.log} 只收 WARN 及以上。
 *
 * <p>测试会重配 JVM 全局 LoggerContext，结束后按 test profile 还原，避免影响后续测试类。
 */
class LogbackConfigurationTest {

    private static final String CONFIG = "classpath:logback-spring.xml";

    private final LogbackLoggingSystem loggingSystem =
            new LogbackLoggingSystem(getClass().getClassLoader());

    @TempDir
    Path logDir;

    @AfterEach
    void restoreTestLogging() {
        var env = new MockEnvironment();
        env.setActiveProfiles("test");
        reinitialize(env);
    }

    @Test
    void prodProfileWritesRollingFilesAndSplitsWarnings() throws IOException {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("zija.log.path", logDir.toString());
        reinitialize(env);

        var log = LoggerFactory.getLogger("com.zija.sample");
        log.debug("debug-line-hidden");
        log.info("info-line-visible");
        log.warn("warn-line-visible");

        var main = Files.readString(logDir.resolve("zija.log"));
        var errors = Files.readString(logDir.resolve("error.log"));
        assertThat(main)
                .contains("info-line-visible", "warn-line-visible")
                .doesNotContain("debug-line-hidden");
        assertThat(errors)
                .contains("warn-line-visible")
                .doesNotContain("info-line-visible");
    }

    private void reinitialize(MockEnvironment env) {
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(env), CONFIG, null);
    }
}

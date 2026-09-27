package com.zija.system.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;

/**
 * 启动完成后输出一行运行配置摘要，便于从日志确认部署形态。
 * <p>
 * 只输出路径、模型名、开关等非敏感项；口令、密码、SMTP 凭据与数据库连接串一律不打印。
 */
@Component
class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    private final Environment environment;

    StartupSummaryLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logSummary() {
        var profiles = environment.getActiveProfiles();
        boolean prod = Arrays.asList(profiles).contains("prod");
        log.info("知家已启动: version={} profiles={} logFiles={} fileStorage={} ollama={} chatModel={} "
                        + "embeddingModel={} mailConfigured={} setupTokenConfigured={}",
                environment.getProperty("info.app.version", "dev"),
                profiles.length == 0 ? "[default]" : String.join(",", profiles),
                prod ? environment.getProperty("zija.log.path", "logs") : "off",
                environment.getProperty("zija.file.storage-path"),
                withoutUserInfo(environment.getProperty("spring.ai.ollama.base-url")),
                environment.getProperty("spring.ai.ollama.chat.model"),
                environment.getProperty("spring.ai.ollama.embedding.model"),
                hasText("spring.mail.host"),
                hasText("zija.setup.token"));
    }

    private static String withoutUserInfo(String url) {
        if (url == null) {
            return null;
        }
        try {
            var uri = URI.create(url);
            if (uri.getUserInfo() == null) {
                return url;
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception invalid) {
            return "<invalid>";
        }
    }

    private boolean hasText(String key) {
        var value = environment.getProperty(key);
        return value != null && !value.isBlank();
    }
}

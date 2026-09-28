package com.zija.system.internal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class StartupSummaryLoggerTest {

    @Test
    void mailConfiguredIgnoresSpringMailHostAndBlankValues() {
        assertThat(StartupSummaryLogger.mailConfigured(null)).isFalse();
        assertThat(StartupSummaryLogger.mailConfigured("")).isFalse();
        assertThat(StartupSummaryLogger.mailConfigured("  ")).isFalse();
        assertThat(StartupSummaryLogger.mailConfigured("false")).isFalse();
        assertThat(StartupSummaryLogger.mailConfigured("FALSE")).isFalse();
        assertThat(StartupSummaryLogger.mailConfigured("smtp.example.com")).isTrue();
    }

    @Test
    void summaryReadsZijaSmtpHost() {
        var environment = new MockEnvironment();
        environment.setProperty("spring.mail.host", "ignored.example.com");
        environment.setProperty("zija.smtp.host", "smtp.example.com");

        assertThat(summary(environment)).contains("mailConfigured=true");
    }

    @Test
    void summaryTreatsFalseHostAsUnconfigured() {
        var environment = new MockEnvironment();
        environment.setProperty("spring.mail.host", "ignored.example.com");
        environment.setProperty("zija.smtp.host", "false");

        assertThat(summary(environment)).contains("mailConfigured=false");
    }

    private static String summary(MockEnvironment environment) {
        Logger logger = (Logger) LoggerFactory.getLogger(StartupSummaryLogger.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            new StartupSummaryLogger(environment).logSummary();
            assertThat(appender.list).isNotEmpty();
            return appender.list.get(appender.list.size() - 1).getFormattedMessage();
        } finally {
            logger.detachAppender(appender);
        }
    }
}

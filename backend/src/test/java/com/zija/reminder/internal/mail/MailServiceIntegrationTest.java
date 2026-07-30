package com.zija.reminder.internal.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.session.jdbc.initialize-schema=never",
    "zija.smtp.from=noreply@zija.local"
})
class MailServiceIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        JavaMailSender mockMailSender() {
            return mock(JavaMailSender.class);
        }
    }

    @Autowired MailService mailService;
    @Autowired JavaMailSender mailSender;

    @BeforeEach
    void clearMock() {
        clearInvocations(mailSender);
    }

    @Test
    void isConfiguredReturnsTrueWhenSenderAndFromPresent() {
        assertThat(mailService.isConfigured()).isTrue();
    }

    @Test
    void sendDigestCallsMailSenderWhenConfigured() {
        mailService.sendDigest("owner@example.com", "<html>body</html>");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendUrgentCallsMailSenderWhenConfigured() {
        mailService.sendUrgent("owner@example.com", "<html>urgent</html>");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendReturnsTrueWhenSuccessful() {
        boolean result = mailService.send("owner@example.com", "subject", "body");
        assertThat(result).isTrue();
    }

    // ---- short-circuit / unconfigured path (unit-level, no Spring context needed) ----

    @Test
    void isConfiguredReturnsFalseWhenSenderIsNull() {
        var unconfigured = new MailService(null, "");
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void isConfiguredReturnsFalseWhenFromIsBlank() {
        var unconfigured = new MailService(mailSender, "   ");
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void sendReturnsFalseWhenNotConfigured() {
        var unconfigured = new MailService(null, "");
        assertThat(unconfigured.send("a@b.com", "subject", "body")).isFalse();
    }

    @Test
    void sendDigestNoOpWhenNotConfigured() {
        var sender = mock(JavaMailSender.class);
        var unconfigured = new MailService(sender, "");
        unconfigured.sendDigest("a@b.com", "<html>body</html>");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendUrgentNoOpWhenNotConfigured() {
        var sender = mock(JavaMailSender.class);
        var unconfigured = new MailService(sender, "");
        unconfigured.sendUrgent("a@b.com", "<html>urgent</html>");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }
}

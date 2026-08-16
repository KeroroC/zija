package com.zija.reminder.internal.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
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
            JavaMailSender sender = mock(JavaMailSender.class);
            when(sender.createMimeMessage())
                    .thenAnswer(inv -> new JavaMailSenderImpl().createMimeMessage());
            return sender;
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
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendUrgentCallsMailSenderWhenConfigured() {
        mailService.sendUrgent("owner@example.com", "<html>urgent</html>");
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ---- regression: HTML must be sent as text/html, not rendered as plain text ----

    @Test
    void sendDigestSendsHtmlBodyPart() throws Exception {
        String html = "<html><body><h1>每日提醒摘要</h1><p>牛奶 3 天后到期</p></body></html>";
        mailService.sendDigest("owner@example.com", html);

        BodyPart htmlPart = findHtmlPart(captureSentMessage());
        assertThat(htmlPart.getContentType()).startsWith("text/html");
        assertThat(htmlPart.getContent()).isEqualTo(html);
    }

    @Test
    void sendUrgentSendsHtmlBodyPart() throws Exception {
        String html = "<html><body><h1>紧急提醒</h1></body></html>";
        mailService.sendUrgent("owner@example.com", html);

        BodyPart htmlPart = findHtmlPart(captureSentMessage());
        assertThat(htmlPart.getContentType()).startsWith("text/html");
        assertThat(htmlPart.getContent()).isEqualTo(html);
    }

    @Test
    void sendSendsHtmlBodyPart() throws Exception {
        String html = "<html><body><p>通用 HTML 邮件</p></body></html>";
        mailService.send("owner@example.com", "subject", html);

        BodyPart htmlPart = findHtmlPart(captureSentMessage());
        assertThat(htmlPart.getContentType()).startsWith("text/html");
        assertThat(htmlPart.getContent()).isEqualTo(html);
    }

    private MimeMessage captureSentMessage() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    /**
     * Materialises MIME headers (as they would appear on the wire), then finds the
     * body part whose content type is text/html, descending through nested multiparts
     * (MimeMessageHelper multipart mode nests html under multipart/related).
     */
    private BodyPart findHtmlPart(MimeMessage msg) throws Exception {
        msg.saveChanges();
        return findHtmlPart(msg.getContent());
    }

    private BodyPart findHtmlPart(Object content) throws Exception {
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.getContent() instanceof Multipart) {
                    BodyPart nested = findHtmlPart(part.getContent());
                    if (nested != null) {
                        return nested;
                    }
                } else if (part.getContentType().startsWith("text/html")) {
                    return part;
                }
            }
        }
        return null;
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
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendUrgentNoOpWhenNotConfigured() {
        var sender = mock(JavaMailSender.class);
        var unconfigured = new MailService(sender, "");
        unconfigured.sendUrgent("a@b.com", "<html>urgent</html>");
        verify(sender, never()).send(any(MimeMessage.class));
    }
}

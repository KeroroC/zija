package com.zija.reminder.internal.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender; // null when SMTP not configured
    private final String from;

    public MailService(@Autowired(required = false) JavaMailSender sender,
                       @Value("${zija.smtp.from:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    /**
     * Returns true when both SMTP host and from-address are configured.
     */
    public boolean isConfigured() {
        return sender != null && !from.isBlank();
    }

    /**
     * Sends an HTML mail message. Returns false silently when not configured or on failure.
     */
    public boolean send(String to, String subject, String html) {
        if (!isConfigured()) {
            return false;
        }
        try {
            var msg = sender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(msg);
            return true;
        } catch (Exception ex) {
            log.warn("邮件发送失败 to={} subject={} err={}", to, subject, ex.getMessage());
            return false;
        }
    }

    /**
     * Sends the daily reminder digest.
     */
    public void sendDigest(String to, String html) {
        if (!isConfigured()) {
            return;
        }
        send(to, "知家 · 每日提醒摘要", html);
    }

    /**
     * Sends an urgent reminder.
     */
    public void sendUrgent(String to, String html) {
        if (!isConfigured()) {
            return;
        }
        send(to, "知家 · 紧急提醒", html);
    }
}

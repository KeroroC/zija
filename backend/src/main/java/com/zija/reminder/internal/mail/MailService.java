package com.zija.reminder.internal.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
     * Sends a simple mail message. Returns false silently when not configured or on failure.
     */
    public boolean send(String to, String subject, String body) {
        if (!isConfigured()) {
            return false;
        }
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            return true;
        } catch (RuntimeException ex) {
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

package com.zija.reminder.internal.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class MailCapabilityConfig {

    /**
     * {@code false} 与空白都不创建发送器。环境变量被设成空字符串时，
     * {@code ${ZIJA_SMTP_HOST:false}} 不会回落到默认值，这里仍视为未配置。
     */
    static boolean isHostConfigured(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return !"false".equalsIgnoreCase(host.trim());
    }

    @Bean
    @Conditional(SmtpHostCondition.class)
    public JavaMailSenderImpl mailSender(@Value("${zija.smtp.host}") String host,
                                         @Value("${zija.smtp.port:587}") int port,
                                         @Value("${zija.smtp.username:}") String user,
                                         @Value("${zija.smtp.password:}") String pass,
                                         @Value("${zija.smtp.tls:true}") boolean tls) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(user);
        sender.setPassword(pass);
        var props = new Properties();
        props.put("mail.smtp.auth", String.valueOf(!user.isEmpty()));
        props.put("mail.smtp.starttls.enable", String.valueOf(tls));
        sender.setJavaMailProperties(props);
        return sender;
    }

    public static final class SmtpHostCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isHostConfigured(context.getEnvironment().getProperty("zija.smtp.host"));
        }
    }
}

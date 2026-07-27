package com.zija.reminder.internal.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class MailCapabilityConfig {

    @Bean
    @ConditionalOnProperty(name = "zija.smtp.host")
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
}

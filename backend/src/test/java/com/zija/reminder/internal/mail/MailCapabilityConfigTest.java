package com.zija.reminder.internal.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailCapabilityConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailCapabilityConfig.class);

    @Test
    void doesNotCreateSenderWhenHostMissingFalseOrBlank() {
        runner.run(context -> assertThat(context).doesNotHaveBean(JavaMailSenderImpl.class));
        runner.withPropertyValues("zija.smtp.host=false")
                .run(context -> assertThat(context).doesNotHaveBean(JavaMailSenderImpl.class));
        runner.withPropertyValues("zija.smtp.host=")
                .run(context -> assertThat(context).doesNotHaveBean(JavaMailSenderImpl.class));
        runner.withPropertyValues("zija.smtp.host=   ")
                .run(context -> assertThat(context).doesNotHaveBean(JavaMailSenderImpl.class));
    }

    @Test
    void createsSenderWhenHostPresent() {
        runner.withPropertyValues(
                        "zija.smtp.host=smtp.example.com",
                        "zija.smtp.port=2525",
                        "zija.smtp.tls=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(JavaMailSenderImpl.class);
                    var sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.example.com");
                    assertThat(sender.getPort()).isEqualTo(2525);
                    assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"))
                            .isEqualTo("false");
                });
    }
}

package com.zija.reminder.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ClockConfig {
    @Bean
    Clock reminderClock() {
        return Clock.systemUTC();
    }
}

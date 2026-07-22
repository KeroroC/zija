package com.zija;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ZijaApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ZijaApplication.class, args);
        // Command-mode runners (e.g. recover-owner) keep non-daemon threads alive unless we exit.
        if (context.getEnvironment().containsProperty("zija.command")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}

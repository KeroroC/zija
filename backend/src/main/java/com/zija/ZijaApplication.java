package com.zija;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 知家应用启动入口。
 *
 * <p>通过 {@link SpringApplication#run} 启动 Spring Boot 应用上下文。
 * 当存在 {@code zija.command} 属性时（命令行模式，如 owner-recovery），
 * 上下文启动完成后立即调用 {@code System.exit} 退出，避免非守护线程阻塞进程。
 */
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

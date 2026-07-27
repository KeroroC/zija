package com.zija;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session JDBC 会话持久化配置。
 *
 * <p>启用基于 JDBC 的服务端会话存储，将会话数据持久化到 PostgreSQL 数据库。
 * 当配置属性 {@code zija.session.jdbc.enabled=false} 时禁用（用于无真实数据源的数据库故障测试场景）。
 *
 * <p>{@code @EnableJdbcHttpSession} 不暴露 cookie 名称属性（Spring Session 4.x），
 * 且会绕过 Spring Boot 自动配置对 {@code server.servlet.session.cookie.name} 的读取，
 * 因此需要显式注册 {@link CookieSerializer} 以确保会话 Cookie 名称与安全配置一致。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession
@ConditionalOnProperty(name = "zija.session.jdbc.enabled", havingValue = "true", matchIfMissing = true)
public class ZijaSessionConfiguration {

    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:ZIJA_SESSION}") String cookieName) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        return serializer;
    }
}

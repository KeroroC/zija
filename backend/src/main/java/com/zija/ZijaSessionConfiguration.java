package com.zija;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.Locale;

/**
 * Spring Session JDBC 会话持久化配置。
 *
 * <p>启用基于 JDBC 的服务端会话存储，将会话数据持久化到 PostgreSQL 数据库。
 * 当配置属性 {@code zija.session.jdbc.enabled=false} 时禁用（用于无真实数据源的数据库故障测试场景）。
 *
 * <p>{@code @EnableJdbcHttpSession} 会绕过 Spring Boot 对会话 Cookie 的自动配置（Spring Session 4.x），
 * 因此这里显式读取 {@code name}、{@code http-only}、{@code same-site}。
 * {@code secure} 不读取：未设置时 {@link DefaultCookieSerializer} 跟随 {@code request.isSecure()}。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession
@ConditionalOnProperty(name = "zija.session.jdbc.enabled", havingValue = "true", matchIfMissing = true)
public class ZijaSessionConfiguration {

    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:ZIJA_SESSION}") String cookieName,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.same-site:Lax}") String sameSite) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setSameSite(canonicalSameSite(sameSite));
        return serializer;
    }

    static String canonicalSameSite(String sameSite) {
        if (sameSite == null || sameSite.isBlank()) {
            return "Lax";
        }
        return switch (sameSite.trim().toLowerCase(Locale.ROOT)) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "server.servlet.session.cookie.same-site must be Lax, Strict, or None");
        };
    }
}

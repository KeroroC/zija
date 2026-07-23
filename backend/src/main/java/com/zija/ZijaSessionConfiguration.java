package com.zija;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Spring Session JDBC 会话持久化配置。
 *
 * <p>启用基于 JDBC 的服务端会话存储，将会话数据持久化到 PostgreSQL 数据库。
 * 当配置属性 {@code zija.session.jdbc.enabled=false} 时禁用（用于无真实数据源的数据库故障测试场景）。
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession
@ConditionalOnProperty(name = "zija.session.jdbc.enabled", havingValue = "true", matchIfMissing = true)
public class ZijaSessionConfiguration {
}

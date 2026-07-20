package com.zija;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Activates Spring Session JDBC for server-side session storage in PostgreSQL.
 *
 * <p>Disabled when {@code zija.session.jdbc.enabled=false} (used by tests that
 * exercise database-failure scenarios without a real DataSource).
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession
@ConditionalOnProperty(name = "zija.session.jdbc.enabled", havingValue = "true", matchIfMissing = true)
public class ZijaSessionConfiguration {
}

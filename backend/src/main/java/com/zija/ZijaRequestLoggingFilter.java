package com.zija;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * 请求摘要日志：方法、路由模板、状态码、耗时。
 * <p>
 * 5xx 记 ERROR；403（越权 / CSRF / 初始化口令错误）与超过 {@code zija.log.slow-request-ms}
 * 的慢请求记 WARN；其余（含业务性 4xx）记 DEBUG，prod 下不输出。
 * <p>
 * 只记路由模板或 URI，不记 query string；附带处理器抛出的异常类名但不记异常消息——
 * 校验异常的消息会回显被拒绝的字段值（可能是密码）。
 * 紧随 {@link ZijaRequestIdFilter}，以便计时覆盖安全链且日志行带上 requestId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ZijaRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ZijaRequestLoggingFilter.class);

    private final long slowRequestMillis;
    private final ObjectProvider<ErrorAttributes> errorAttributes;

    public ZijaRequestLoggingFilter(
            @Value("${zija.log.slow-request-ms:1000}") long slowRequestMillis,
            ObjectProvider<ErrorAttributes> errorAttributes
    ) {
        this.slowRequestMillis = slowRequestMillis;
        this.errorAttributes = errorAttributes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            logSummary(request, status, elapsedMillis);
        }
    }

    private void logSummary(HttpServletRequest request, int status, long elapsedMillis) {
        var accountId = (String) request.getAttribute(ZijaAccountMdcFilter.ATTRIBUTE);
        if (accountId != null) {
            MDC.put(ZijaAccountMdcFilter.MDC_KEY, accountId);
        }
        try {
            var route = route(request);
            var cause = handlerException(request);
            if (status >= 500) {
                log.error("{} {} -> {} ({} ms){}", request.getMethod(), route, status, elapsedMillis, cause);
            } else if (status == HttpServletResponse.SC_FORBIDDEN) {
                log.warn("拒绝访问 {} {} -> {} ({} ms){}", request.getMethod(), route, status, elapsedMillis, cause);
            } else if (elapsedMillis >= slowRequestMillis) {
                log.warn("慢请求 {} {} -> {} ({} ms){}", request.getMethod(), route, status, elapsedMillis, cause);
            } else {
                log.debug("{} {} -> {} ({} ms){}", request.getMethod(), route, status, elapsedMillis, cause);
            }
        } finally {
            MDC.remove(ZijaAccountMdcFilter.MDC_KEY);
        }
    }

    private String handlerException(HttpServletRequest request) {
        var attributes = errorAttributes.getIfAvailable();
        var error = attributes != null ? attributes.getError(new ServletWebRequest(request)) : null;
        return error != null ? " [" + error.getClass().getSimpleName() + "]" : "";
    }

    private static String route(HttpServletRequest request) {
        var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}

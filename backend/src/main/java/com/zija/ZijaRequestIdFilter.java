package com.zija;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求 ID 过滤器。
 * <p>
 * 为每个 HTTP 请求生成或提取唯一的请求标识符（{@code X-Request-Id}），用于全链路追踪。
 * 如果客户端提供了合法的请求 ID 则复用，否则自动生成 UUID。
 * 该 ID 会同时写入响应头、请求属性和 SLF4J MDC，便于日志关联。
 *
 * @see #HEADER
 * @see #ATTRIBUTE
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZijaRequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "zija.request-id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var supplied = request.getHeader(HEADER);
        var requestId = supplied != null
                && SAFE_REQUEST_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();

        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}

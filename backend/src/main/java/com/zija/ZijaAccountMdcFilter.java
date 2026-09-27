package com.zija;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把已认证账户 ID 写入 SLF4J MDC（{@code accountId}），让该请求内的每行日志都能关联到操作人。
 * <p>
 * 只放 UUID，不放用户名等个人信息。同时写入请求属性，供外层
 * {@link ZijaRequestLoggingFilter} 在 MDC 清理后仍能输出请求摘要。
 * 必须排在 Spring Security 过滤器链（默认 order -100）之后，否则 SecurityContext 尚未就绪。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ZijaAccountMdcFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "accountId";
    public static final String ATTRIBUTE = "zija.account-id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ZijaPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        var accountId = principal.getAccountId().toString();
        request.setAttribute(ATTRIBUTE, accountId);
        MDC.put(MDC_KEY, accountId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

package com.zija;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * 会话认证支持组件。
 * <p>
 * 封装基于 Session 的用户名/密码认证流程，包括：
 * <ul>
 *   <li>调用 {@link AuthenticationManager} 执行认证</li>
 *   <li>会话固定攻击防护（{@link ChangeSessionIdAuthenticationStrategy}）</li>
 *   <li>安全上下文持久化到 HttpSession</li>
 *   <li>Spring Session 主索引写入，支持按账户查询会话</li>
 *   <li>CSRF 令牌重新生成</li>
 * </ul>
 */
@Component
public class ZijaSessionAuthenticationSupport {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;

    public ZijaSessionAuthenticationSupport(
            AuthenticationManager authenticationManager,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.securityContextRepository = new HttpSessionSecurityContextRepository();
        this.sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    }

    /**
     * 执行用户名密码认证并建立安全会话。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 认证成功的 {@link Authentication} 对象
     */
    public Authentication authenticate(
            String username,
            String password,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var token = new UsernamePasswordAuthenticationToken(username, password);
        var authentication = authenticationManager.authenticate(token);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        var principal = (ZijaPrincipal) authentication.getPrincipal();
        request.getSession().setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                principal.getAccountId().toString());
        regenerateCsrfToken(request, response);

        return authentication;
    }

    /**
     * 重新生成 CSRF 令牌。
     * <p>
     * 先清除旧令牌，再通过延迟令牌机制生成新令牌并写入响应。
     */
    public void regenerateCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        csrfTokenRepository.saveToken(null, request, response);
        csrfTokenRepository.loadDeferredToken(request, response).get();
    }

    /**
     * 用新主体刷新当前会话的认证信息（不轮换会话 ID）。
     * <p>
     * 用于就地更新会话内的 {@link ZijaPrincipal}（例如改名后），
     * 使后续请求（含审计 actor 名）立即反映新值。会话主索引按 accountId
     * 存储，主体变更不影响索引。
     *
     * @param principal 新的认证主体
     * @param request   当前 HTTP 请求
     * @param response  当前 HTTP 响应
     * @return 重新构造的已认证 {@link Authentication}
     */
    public Authentication refreshPrincipal(
            ZijaPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        request.getSession().setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                principal.getAccountId().toString());
        return authentication;
    }
}

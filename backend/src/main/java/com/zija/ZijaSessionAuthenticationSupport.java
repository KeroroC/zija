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

    public void regenerateCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        csrfTokenRepository.saveToken(null, request, response);
        csrfTokenRepository.loadDeferredToken(request, response).get();
    }
}

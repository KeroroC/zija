package com.zija;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZijaSessionAuthenticationSupportTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticationRotatesAndGeneratesCsrfToken() {
        var authenticationManager = mock(AuthenticationManager.class);
        var csrfRepository = mock(CsrfTokenRepository.class);
        var authentication = mock(Authentication.class);
        var principal = new ZijaPrincipal(
                UUID.randomUUID(), "owner", "所有者", "{bcrypt}hash", true);
        var deferredToken = mock(DeferredCsrfToken.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(csrfRepository.loadDeferredToken(request, response)).thenReturn(deferredToken);

        var support = new ZijaSessionAuthenticationSupport(
                authenticationManager, csrfRepository);

        support.authenticate("owner", "Passw0rd!", request, response);

        var inOrder = inOrder(csrfRepository, deferredToken);
        inOrder.verify(csrfRepository).saveToken(null, request, response);
        inOrder.verify(csrfRepository).loadDeferredToken(request, response);
        inOrder.verify(deferredToken).get();
    }

    @Test
    void authenticationIndexesSessionByAccountId() {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "owner", "所有者", "{bcrypt}hash", true);
        var authenticationManager = mock(AuthenticationManager.class);
        var csrfRepository = mock(CsrfTokenRepository.class);
        var authentication = mock(Authentication.class);
        var deferredToken = mock(DeferredCsrfToken.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authentication);
        when(csrfRepository.loadDeferredToken(request, response)).thenReturn(deferredToken);

        var support = new ZijaSessionAuthenticationSupport(
                authenticationManager, csrfRepository);

        support.authenticate("owner", "Passw0rd!", request, response);

        assertThat(request.getSession().getAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
                .isEqualTo(accountId.toString());
    }

    @Test
    void explicitRegenerationUsesDeferredTokenGeneration() {
        var csrfRepository = mock(CsrfTokenRepository.class);
        var deferredToken = mock(DeferredCsrfToken.class);
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();
        when(csrfRepository.loadDeferredToken(request, response)).thenReturn(deferredToken);

        var support = new ZijaSessionAuthenticationSupport(
                mock(AuthenticationManager.class), csrfRepository);

        support.regenerateCsrfToken(request, response);

        var inOrder = inOrder(csrfRepository, deferredToken);
        inOrder.verify(csrfRepository).saveToken(null, request, response);
        inOrder.verify(csrfRepository).loadDeferredToken(request, response);
        inOrder.verify(deferredToken).get();
    }
}

package com.rafael.autenticacao.Authentication.session.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAuthenticationStrategyTest {

    @Test
    void strategyShouldChangeSessionIdAndClearCsrfTokenAfterAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String sessionIdBeforeAuthentication = request.getSession(true).getId();
        CsrfTokenRepository csrfTokenRepository = mock(CsrfTokenRepository.class);
        DeferredCsrfToken deferredCsrfToken = mock(DeferredCsrfToken.class);
        when(csrfTokenRepository.loadToken(request))
                .thenReturn(new DefaultCsrfToken(
                        "X-XSRF-TOKEN",
                        "_csrf",
                        "csrf-token-before-login"
                ));
        when(csrfTokenRepository.loadDeferredToken(request, response)).thenReturn(deferredCsrfToken);
        var authentication = new TestingAuthenticationToken(
                "rafael@email.com",
                null,
                "ROLE_USER"
        );
        var strategy = new SecurityConfigBySession()
                .sessionAuthenticationStrategy(csrfTokenRepository);

        strategy.onAuthentication(authentication, request, response);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getId())
                .isNotEqualTo(sessionIdBeforeAuthentication);
        verify(csrfTokenRepository).saveToken(null, request, response);
    }
}

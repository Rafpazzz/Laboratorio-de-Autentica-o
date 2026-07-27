package com.rafael.autenticacao.Authentication.session.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCsrfTokenRepositoryTest {

    @Test
    void clearingTokenShouldExpireXsrfCookie() {
        CsrfTokenRepository repository = new SecurityConfigBySession()
                .sessionCsrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(null, request, response);

        Cookie expiredCookie = response.getCookie("XSRF-TOKEN");
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getValue()).isEmpty();
        assertThat(expiredCookie.getMaxAge()).isZero();
        assertThat(expiredCookie.getPath()).isEqualTo("/");
    }
}

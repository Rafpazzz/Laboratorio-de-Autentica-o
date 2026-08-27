package com.rafael.autenticacao.Authentication.jwt.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCsrfTokenRepositoryTest {

    private static final String COOKIE_NAME = "JWT-XSRF-TOKEN";
    private static final String HEADER_NAME = "X-JWT-XSRF-TOKEN";

    @Test
    void shouldPersistJwtCsrfTokenInReadableSecureCookie() {
        CsrfTokenRepository repository = new JwtCsrfConfig()
                .jwtCsrfTokenRepository(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken csrfToken = repository.generateToken(request);

        repository.saveToken(csrfToken, request, response);

        Cookie cookie = response.getCookie(COOKIE_NAME);
        assertThat(csrfToken.getHeaderName()).isEqualTo(HEADER_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(csrfToken.getToken());
        assertThat(cookie.getPath()).isEqualTo("/auth/jwt");
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void clearingTokenShouldExpireNonSecureJwtCsrfCookie() {
        CsrfTokenRepository repository = new JwtCsrfConfig()
                .jwtCsrfTokenRepository(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(null, request, response);

        Cookie expiredCookie = response.getCookie(COOKIE_NAME);
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getValue()).isEmpty();
        assertThat(expiredCookie.getMaxAge()).isZero();
        assertThat(expiredCookie.getPath()).isEqualTo("/auth/jwt");
        assertThat(expiredCookie.getSecure()).isFalse();
        assertThat(expiredCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }
}

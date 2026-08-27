package com.rafael.autenticacao.Authentication.jwt.refresh.cookie;

import com.rafael.autenticacao.Authentication.jwt.refresh.service.IssuedRefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRefreshTokenCookieFactoryTest {

    private static final Duration TOKEN_TTL = Duration.ofHours(2);

    @Test
    void shouldCreateSecureHttpOnlyCookieWithRestrictedPathAndExpiration() {
        var cookieFactory = new JwtRefreshTokenCookieFactory(true);
        var issuedToken = new IssuedRefreshToken(
                "raw-refresh-token",
                Instant.now().plus(TOKEN_TTL)
        );

        ResponseCookie cookie = cookieFactory.create(issuedToken);

        assertThat(cookie.getName()).isEqualTo(JwtRefreshTokenCookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo(issuedToken.tokenValue());
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/auth/jwt");
        assertThat(cookie.getMaxAge())
                .isPositive()
                .isLessThanOrEqualTo(TOKEN_TTL)
                .isGreaterThan(TOKEN_TTL.minusSeconds(2));
    }

    @Test
    void shouldAllowNonSecureCookieForLocalDevelopment() {
        var cookieFactory = new JwtRefreshTokenCookieFactory(false);
        var issuedToken = new IssuedRefreshToken(
                "raw-refresh-token",
                Instant.now().plus(TOKEN_TTL)
        );

        ResponseCookie cookie = cookieFactory.create(issuedToken);

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void shouldClearRefreshCookieUsingTheSameSecurityAttributes() {
        var cookieFactory = new JwtRefreshTokenCookieFactory(true);

        ResponseCookie cookie = cookieFactory.clear();

        assertThat(cookie.getName()).isEqualTo(JwtRefreshTokenCookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/auth/jwt");
    }
}

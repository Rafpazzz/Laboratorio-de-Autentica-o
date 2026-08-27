package com.rafael.autenticacao.Authentication.jwt.refresh.cookie;

import com.rafael.autenticacao.Authentication.jwt.refresh.service.IssuedRefreshToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class JwtRefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private final boolean secure;

    public JwtRefreshTokenCookieFactory(@Value("${app.security.jwt.refresh-cookie-secure}")boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(IssuedRefreshToken issuedToken) {
        Duration maxAge = Duration.between(Instant.now(), issuedToken.expiresAt());

        return ResponseCookie.from(COOKIE_NAME, issuedToken.tokenValue())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/auth/jwt")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME,"")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/auth/jwt")
                .maxAge(Duration.ZERO)
                .build();
    }
}

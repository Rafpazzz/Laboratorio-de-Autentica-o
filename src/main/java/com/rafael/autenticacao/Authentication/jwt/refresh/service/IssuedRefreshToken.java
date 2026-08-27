package com.rafael.autenticacao.Authentication.jwt.refresh.service;

import java.time.Instant;

public record IssuedRefreshToken(
        String tokenValue,
        Instant expiresAt
) {
}

package com.rafael.autenticacao.Authentication.jwt.refresh.service;

import org.springframework.security.core.Authentication;

public record RotatedRefreshToken(IssuedRefreshToken issuedRefreshToken, Authentication authentication) {
}

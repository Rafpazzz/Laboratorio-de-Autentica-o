package com.rafael.autenticacao.Authentication.jwt.refresh.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException() {
        super("Refresh token invalido ou expirado");
    }
}

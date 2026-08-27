package com.rafael.autenticacao.Authentication.jwt.dto;

public record JwtLoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}

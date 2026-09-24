package com.rafael.autenticacao.Authentication.jwt.dto;

import java.util.List;

public record JwtUserResponseDTO(
        String subject,
        String username,
        String name,
        String email,
        List<String> authorities
) {
}

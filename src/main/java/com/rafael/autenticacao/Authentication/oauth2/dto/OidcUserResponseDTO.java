package com.rafael.autenticacao.Authentication.oauth2.dto;

import java.util.List;

public record OidcUserResponseDTO(
        String subject,
        String username,
        String name,
        String email,
        Boolean emailVerified,
        List<String> authorities
) {
}

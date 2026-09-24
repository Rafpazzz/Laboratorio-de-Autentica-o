package com.rafael.autenticacao.Authentication.saml.dto;

import java.util.List;

public record SamlUserResponseDTO(
        String nameId,
        String username,
        String name,
        String email,
        String registrationId,
        List<String> authorities
) {
}

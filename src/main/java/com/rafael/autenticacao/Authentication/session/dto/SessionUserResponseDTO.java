package com.rafael.autenticacao.Authentication.session.dto;

import java.util.List;
import java.util.UUID;

public record SessionUserResponseDTO(
        UUID id,
        String username,
        String name,
        String email,
        List<String> authorities
) {
}

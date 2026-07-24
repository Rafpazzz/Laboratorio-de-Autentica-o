package com.rafael.autenticacao.Usuario.Dto;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;

import java.util.UUID;

public record UsuarioResponse(
        String name,
        String email,
        int age
) {
    public static UsuarioResponse fromEntity(Entidade user) {
        return new UsuarioResponse(
                user.getName(),
                user.getEmail(),
                user.getAge()
        );
    }
}

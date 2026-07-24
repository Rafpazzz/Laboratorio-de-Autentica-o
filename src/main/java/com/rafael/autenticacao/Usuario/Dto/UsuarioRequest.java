package com.rafael.autenticacao.Usuario.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank(message = "nome nao pode vir vazio")
        String name,

        @NotBlank(message = "email nao pode vir vazio")
        @Email(message = "email deve ser valido")
        String email,

        @NotBlank(message = "senha nao pode vir vazia")
        String password,

        @NotNull(message = "idade nao pode ser nula")
        @Min(value = 0, message = "idade nao pode ser negativa")
        Integer age
) {
}

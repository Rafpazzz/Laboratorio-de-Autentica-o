package com.rafael.autenticacao.Authentication.shared.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequestDTO(
        @NotBlank(message = "Usuario deve conter um nome")
        String nome,
        @NotBlank(message = "Usuario deve conter um email")
        @Email(message = "email deve ser valido")
        String email,
        @NotBlank(message = "usario deve conter uma senha")
        String password,
        @Min(value = 1, message = "o usuario nao pode ter uma idade menor do que 1")
        int age
    ) {
}

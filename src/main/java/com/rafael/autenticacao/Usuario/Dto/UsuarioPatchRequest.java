package com.rafael.autenticacao.Usuario.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;

public record UsuarioPatchRequest(
        String name,

        @Email(message = "email deve ser valido")
        String email,

        @Min(value = 0, message = "idade nao pode ser negativa")
        Integer age
) {
}

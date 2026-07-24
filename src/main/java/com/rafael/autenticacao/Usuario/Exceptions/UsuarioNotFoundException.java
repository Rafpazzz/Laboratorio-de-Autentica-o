package com.rafael.autenticacao.Usuario.Exceptions;

public class UsuarioNotFoundException extends RuntimeException {
    public UsuarioNotFoundException() {
        super("Usuario nao encontrado");
    }
}

package com.rafael.autenticacao.Usuario.Exceptions;

public class ExistEmailException extends RuntimeException {
    public ExistEmailException(String message) {
        super(message);
    }

    public ExistEmailException(){super("Email ja cadastrado");}
}

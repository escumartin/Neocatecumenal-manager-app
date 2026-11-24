package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("Credenciales incorrectas");
    }
}

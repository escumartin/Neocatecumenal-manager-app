package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

public class OriginNotAllowedException extends RuntimeException {

    public OriginNotAllowedException() {
        super("Origen no permitido");
    }
}

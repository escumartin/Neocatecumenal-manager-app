package io.github.escumartin.neocatecumenal.identity.application.port.out;

public interface SessionTokenIssuer {

    IssuedSessionToken issue();

    byte[] hash(String token);
}

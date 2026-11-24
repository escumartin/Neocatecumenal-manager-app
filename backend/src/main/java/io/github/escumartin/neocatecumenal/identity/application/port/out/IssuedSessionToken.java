package io.github.escumartin.neocatecumenal.identity.application.port.out;

public record IssuedSessionToken(String token, byte[] hash) {
}

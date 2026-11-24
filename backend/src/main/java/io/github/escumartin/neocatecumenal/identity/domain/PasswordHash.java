package io.github.escumartin.neocatecumenal.identity.domain;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("REQUIRED");
        }
    }
}

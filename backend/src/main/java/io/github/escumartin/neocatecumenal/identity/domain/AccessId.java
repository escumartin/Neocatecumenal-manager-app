package io.github.escumartin.neocatecumenal.identity.domain;

public record AccessId(int value) {

    public static final int MIN = 100000;
    public static final int MAX = 999999;

    public AccessId {
        if (value < MIN || value > MAX) {
            throw new DomainException("INVALID_FORMAT");
        }
    }
}

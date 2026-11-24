package io.github.escumartin.neocatecumenal.identity.domain;

public enum UserRole {
    REGISTERED,
    MODERATOR,
    ADMINISTRATOR;

    public boolean includes(UserRole other) {
        return ordinal() >= other.ordinal();
    }
}

package io.github.escumartin.neocatecumenal.identity;

public enum Role {
    REGISTERED,
    MODERATOR,
    ADMINISTRATOR;

    public boolean includes(Role required) {
        return ordinal() >= required.ordinal();
    }
}

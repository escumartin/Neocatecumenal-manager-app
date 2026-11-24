package io.github.escumartin.neocatecumenal.identity.domain;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new DomainException("REQUIRED");
        }
    }
}

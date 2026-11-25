package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value);
    }
}

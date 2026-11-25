package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value);
    }
}

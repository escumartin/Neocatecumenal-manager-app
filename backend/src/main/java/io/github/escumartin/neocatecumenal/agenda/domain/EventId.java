package io.github.escumartin.neocatecumenal.agenda.domain;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value);
    }
}

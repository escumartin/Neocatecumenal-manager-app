package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public record RoomId(UUID value) {

    public RoomId {
        Objects.requireNonNull(value);
    }
}

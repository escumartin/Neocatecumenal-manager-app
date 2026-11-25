package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value);
    }
}

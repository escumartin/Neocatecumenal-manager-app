package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public record BookingRequestId(UUID value) {

    public BookingRequestId {
        Objects.requireNonNull(value);
    }
}

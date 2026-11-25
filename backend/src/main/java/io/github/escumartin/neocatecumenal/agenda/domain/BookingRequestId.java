package io.github.escumartin.neocatecumenal.agenda.domain;

import java.util.Objects;
import java.util.UUID;

public record BookingRequestId(UUID value) {

    public BookingRequestId {
        Objects.requireNonNull(value);
    }
}

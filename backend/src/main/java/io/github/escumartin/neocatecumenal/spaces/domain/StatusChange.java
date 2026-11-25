package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.Instant;
import java.util.Objects;

public record StatusChange(BookingId bookingId, BookingRequestStatus from, BookingRequestStatus to, UserId changedBy,
        Instant changedAt) {

    public StatusChange {
        Objects.requireNonNull(to);
        Objects.requireNonNull(changedAt);
    }
}

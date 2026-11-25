package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record Booking(BookingId id, LocalDate date, TimeSlot slot, BookingStatus status) {

    public Booking {
        Objects.requireNonNull(id);
        Objects.requireNonNull(date);
        Objects.requireNonNull(slot);
        Objects.requireNonNull(status);
    }

    public Booking withStatus(BookingStatus newStatus) {
        return new Booking(id, date, slot, newStatus);
    }

    public boolean hasStarted(LocalDateTime now) {
        return !date.atTime(slot.start()).isAfter(now);
    }
}

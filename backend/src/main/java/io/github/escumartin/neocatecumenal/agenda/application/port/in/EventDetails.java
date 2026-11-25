package io.github.escumartin.neocatecumenal.agenda.application.port.in;

import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventBookingStatus;
import java.util.Objects;

public record EventDetails(Event event, EventBookingStatus bookingStatus) {

    public EventDetails {
        Objects.requireNonNull(event);
        Objects.requireNonNull(bookingStatus);
    }
}

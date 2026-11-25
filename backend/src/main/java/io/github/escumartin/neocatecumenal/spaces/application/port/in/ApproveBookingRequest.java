package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;

public interface ApproveBookingRequest {

    BookingRequest approve(Actor actor, BookingRequestId id);
}

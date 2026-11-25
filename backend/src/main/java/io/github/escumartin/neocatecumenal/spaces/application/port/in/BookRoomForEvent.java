package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import java.util.Optional;
import java.util.UUID;

public interface BookRoomForEvent {

    EventBookingResult requestForEvent(EventBookingCommand command);

    Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId);
}

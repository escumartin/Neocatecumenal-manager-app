package io.github.escumartin.neocatecumenal.spaces;

import java.util.Optional;
import java.util.UUID;

public interface SpacesApi {

    EventBookingResult requestForEvent(EventBookingCommand command);

    Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId);
}

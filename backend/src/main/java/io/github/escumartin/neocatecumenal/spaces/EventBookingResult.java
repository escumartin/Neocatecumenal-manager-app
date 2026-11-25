package io.github.escumartin.neocatecumenal.spaces;

import java.util.List;
import java.util.UUID;

public sealed interface EventBookingResult {

    record Booked(UUID bookingRequestId) implements EventBookingResult {
    }

    record Occupied(List<EventBookingConflict> conflicts) implements EventBookingResult {

        public Occupied {
            conflicts = List.copyOf(conflicts);
        }
    }

    record RoomUnavailable(Reason reason) implements EventBookingResult {

        public enum Reason {
            NOT_FOUND,
            INACTIVE
        }
    }
}

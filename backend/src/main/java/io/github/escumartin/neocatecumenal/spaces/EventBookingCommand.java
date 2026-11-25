package io.github.escumartin.neocatecumenal.spaces;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public record EventBookingCommand(UUID eventId, UUID roomId, UUID communityId, UUID requestedBy, LocalDate date,
        LocalTime startTime, LocalTime endTime, String reason) {

    public EventBookingCommand {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(communityId);
        Objects.requireNonNull(requestedBy);
        Objects.requireNonNull(date);
        Objects.requireNonNull(startTime);
        Objects.requireNonNull(endTime);
        Objects.requireNonNull(reason);
    }
}

package io.github.escumartin.neocatecumenal.agenda.application;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FakeSpaces implements SpacesApi {

    final List<EventBookingCommand> commands = new ArrayList<>();
    final Map<UUID, BookingRequestState> states = new HashMap<>();
    UUID nextBookingRequestId = UUID.randomUUID();
    EventBookingResult result;

    FakeSpaces() {
        result = new EventBookingResult.Booked(nextBookingRequestId);
    }

    @Override
    public EventBookingResult requestForEvent(EventBookingCommand command) {
        commands.add(command);
        if (result instanceof EventBookingResult.Booked booked) {
            states.put(booked.bookingRequestId(), BookingRequestState.PENDING);
        }
        return result;
    }

    @Override
    public boolean hasBookingRequestsFor(UUID communityId) {
        return false;
    }

    @Override
    public Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId) {
        return Optional.ofNullable(states.get(bookingRequestId));
    }
}

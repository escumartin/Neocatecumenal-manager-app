package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingConflict;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Occupied;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.BookRoomForEvent;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class SpacesApiAdapter implements SpacesApi {

    private static final Logger log = LoggerFactory.getLogger(SpacesApiAdapter.class);

    private final BookRoomForEvent bookRoomForEvent;
    private final BookingRequestRepository requests;

    public SpacesApiAdapter(BookRoomForEvent bookRoomForEvent, BookingRequestRepository requests) {
        this.bookRoomForEvent = bookRoomForEvent;
        this.requests = requests;
    }

    @Override
    public EventBookingResult requestForEvent(EventBookingCommand command) {
        try {
            return bookRoomForEvent.requestForEvent(command);
        } catch (RoomOccupied raced) {
            return occupied(raced.conflicts());
        }
    }

    @Override
    public Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId) {
        return bookRoomForEvent.bookingRequestStatus(bookingRequestId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBookingRequestsFor(UUID communityId) {
        return requests.existsByCommunity(new CommunityId(communityId));
    }

    static Occupied occupied(List<Conflict> conflicts) {
        log.atInfo().addKeyValue("conflicts", conflicts.size())
                .log("Solicitud de salón para un evento rechazada por ocupación");
        return new Occupied(conflicts.stream()
                .map(conflict -> new EventBookingConflict(conflict.date(), conflict.slot().start(),
                        conflict.slot().end()))
                .toList());
    }
}

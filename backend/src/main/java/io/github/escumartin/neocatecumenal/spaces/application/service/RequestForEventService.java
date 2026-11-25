package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Booked;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.RoomUnavailable;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.BookRoomForEvent;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.EventId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RequestForEventService implements BookRoomForEvent {

    private static final Logger log = LoggerFactory.getLogger(RequestForEventService.class);

    private final RoomRepository rooms;
    private final BookingRequestRepository requests;
    private final Clock clock;

    public RequestForEventService(RoomRepository rooms, BookingRequestRepository requests, Clock clock) {
        this.rooms = rooms;
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventBookingResult requestForEvent(EventBookingCommand command) {
        Optional<Room> found = rooms.findByIdForShare(new RoomId(command.roomId()));
        if (found.isEmpty()) {
            return new RoomUnavailable(RoomUnavailable.Reason.NOT_FOUND);
        }
        Room room = found.get();
        if (!room.active()) {
            return new RoomUnavailable(RoomUnavailable.Reason.INACTIVE);
        }
        CommunityId community = new CommunityId(command.communityId());
        TimeSlot slot = new TimeSlot(command.startTime(), command.endTime());
        BookingRequest request = BookingRequest.forEvent(new UserId(command.requestedBy()), community, room.id(),
                command.date(), slot, new Reason(command.reason()), new EventId(command.eventId()),
                LocalDateTime.now(clock), clock.instant());
        List<Conflict> conflicts = requests.findConflicts(room.id(),
                request.bookings().stream().map(Booking::date).toList(), slot);
        if (!conflicts.isEmpty()) {
            return SpacesApiAdapter.occupied(conflicts);
        }
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value())
                .log("Solicitud de salón registrada para un evento");
        return new Booked(saved.id().value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId) {
        return requests.findById(new BookingRequestId(bookingRequestId))
                .map(request -> BookingRequestState.valueOf(request.status().name()));
    }
}

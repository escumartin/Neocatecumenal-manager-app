package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RequestBookingService implements RequestBooking {

    private static final Logger log = LoggerFactory.getLogger(RequestBookingService.class);

    private final RoomRepository rooms;
    private final BookingRequestRepository requests;
    private final Clock clock;

    public RequestBookingService(RoomRepository rooms, BookingRequestRepository requests, Clock clock) {
        this.rooms = rooms;
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingRequest request(Actor actor, RoomId roomId, CommunityId requestedCommunity, LocalDate date,
            TimeSlot slot, Reason reason) {
        return request(actor, roomId, requestedCommunity, date, slot, reason, null);
    }

    @Override
    @Transactional
    public BookingRequest request(Actor actor, RoomId roomId, CommunityId requestedCommunity, LocalDate date,
            TimeSlot slot, Reason reason, WeeklyRecurrence recurrence) {
        CommunityId community = BookingPolicy.actingCommunity(actor, requestedCommunity);
        Room room = rooms.findByIdForShare(roomId).orElseThrow(RoomNotFound::new);
        room.requireActive();
        BookingRequest request = BookingRequest.request(actor, room.id(), community, date, slot, reason,
                recurrence, LocalDateTime.now(clock), clock.instant());
        List<Conflict> conflicts = requests.findConflicts(room.id(),
                request.bookings().stream().map(Booking::date).toList(), slot);
        if (!conflicts.isEmpty()) {
            log.atInfo().addKeyValue("conflicts", conflicts.size()).log("Solicitud de salón rechazada por ocupación");
            throw new RoomOccupied(conflicts);
        }
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value()).log("Solicitud de salón registrada");
        return saved;
    }
}

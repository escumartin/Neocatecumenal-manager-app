package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.DeactivateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DeactivateRoomService implements DeactivateRoom {

    private static final Logger log = LoggerFactory.getLogger(DeactivateRoomService.class);

    private final RoomRepository rooms;
    private final BookingCounter bookings;
    private final Clock clock;

    public DeactivateRoomService(RoomRepository rooms, BookingCounter bookings, Clock clock) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deactivate(Actor actor, RoomId roomId) {
        RoomPolicy.requireAdministrator(actor);
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(RoomNotFound::new);
        int activeFutureBookings = bookings.activeFutureBookings(roomId, LocalDateTime.now(clock));
        room.deactivate(actor, activeFutureBookings);
        rooms.save(room);
        log.atInfo().addKeyValue("roomId", roomId.value()).log("Salón dado de baja");
    }
}

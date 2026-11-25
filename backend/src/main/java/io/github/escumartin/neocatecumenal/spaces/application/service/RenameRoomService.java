package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RenameRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RenameRoomService implements RenameRoom {

    private static final Logger log = LoggerFactory.getLogger(RenameRoomService.class);

    private final RoomRepository rooms;

    public RenameRoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    @Transactional
    public Room rename(Actor actor, RoomId roomId, RoomName name) {
        RoomPolicy.requireAdministrator(actor);
        Room room = rooms.findById(roomId).orElseThrow(RoomNotFound::new);
        RoomName previous = room.name();
        room.rename(actor, name);
        if (!previous.sameAs(name) && rooms.existsActiveByName(name)) {
            throw new RoomNameTaken();
        }
        Room saved = rooms.save(room);
        log.atInfo().addKeyValue("roomId", saved.id().value()).log("Salón renombrado");
        return saved;
    }
}

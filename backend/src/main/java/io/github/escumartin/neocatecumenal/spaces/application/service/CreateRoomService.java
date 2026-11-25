package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.CreateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CreateRoomService implements CreateRoom {

    private static final Logger log = LoggerFactory.getLogger(CreateRoomService.class);

    private final RoomRepository rooms;

    public CreateRoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    @Transactional
    public Room create(Actor actor, RoomName name) {
        Room room = Room.create(actor, name);
        if (rooms.existsActiveByName(name)) {
            throw new RoomNameTaken();
        }
        Room saved = rooms.save(room);
        log.atInfo().addKeyValue("roomId", saved.id().value()).log("Salón creado");
        return saved;
    }
}

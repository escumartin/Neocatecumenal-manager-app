package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListRooms;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ListRoomsService implements ListRooms {

    private final RoomRepository rooms;

    public ListRoomsService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> list() {
        return rooms.findAllActive();
    }
}

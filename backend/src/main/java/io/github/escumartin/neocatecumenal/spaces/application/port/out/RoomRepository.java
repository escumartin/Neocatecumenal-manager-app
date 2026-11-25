package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    Optional<Room> findById(RoomId id);

    Optional<Room> findByIdForUpdate(RoomId id);

    Optional<Room> findByIdForShare(RoomId id);

    List<Room> findAllActive();

    boolean existsActiveByName(RoomName name);

    Room save(Room room);
}

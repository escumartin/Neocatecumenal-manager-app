package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import java.time.LocalDate;
import java.util.List;

public interface RoomOccupancyQuery {

    List<OccupiedBooking> occupiedSlots(RoomId roomId, LocalDate date);
}

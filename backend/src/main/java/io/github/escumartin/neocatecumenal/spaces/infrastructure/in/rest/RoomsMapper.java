package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.model.OccupiedSlot;
import io.github.escumartin.neocatecumenal.generated.model.Room;
import io.github.escumartin.neocatecumenal.generated.model.RoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import java.time.format.DateTimeFormatter;

final class RoomsMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private RoomsMapper() {
    }

    static Room toResponse(io.github.escumartin.neocatecumenal.spaces.domain.Room room) {
        return new Room().id(room.id().value()).name(room.name().value()).active(room.active());
    }

    static RoomOccupancy toResponse(GetRoomOccupancy.RoomOccupancy occupancy) {
        return new RoomOccupancy().roomId(occupancy.roomId().value()).date(occupancy.date())
                .occupied(occupancy.occupied().stream().map(RoomsMapper::toResponse).toList());
    }

    private static OccupiedSlot toResponse(GetRoomOccupancy.OccupiedSlot slot) {
        return new OccupiedSlot().startTime(TIME.format(slot.slot().start())).endTime(TIME.format(slot.slot().end()))
                .status(OccupiedSlot.StatusEnum.valueOf(slot.status().name())).ownCommunity(slot.ownCommunity());
    }

    static RoomName toName(String name) {
        return new RoomName(name);
    }
}

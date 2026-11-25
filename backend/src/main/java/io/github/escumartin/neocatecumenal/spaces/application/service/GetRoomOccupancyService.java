package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class GetRoomOccupancyService implements GetRoomOccupancy {

    private final RoomRepository rooms;
    private final RoomOccupancyQuery occupancy;

    public GetRoomOccupancyService(RoomRepository rooms, RoomOccupancyQuery occupancy) {
        this.rooms = rooms;
        this.occupancy = occupancy;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomOccupancy get(Actor actor, RoomId roomId, LocalDate date, CommunityId requestedCommunity) {
        CommunityId community = BookingPolicy.actingCommunity(actor, requestedCommunity);
        Room room = rooms.findById(roomId).filter(Room::active).orElseThrow(RoomNotFound::new);
        List<OccupiedSlot> slots = occupancy.occupiedSlots(room.id(), date).stream()
                .map(booking -> new OccupiedSlot(booking.slot(), booking.status(),
                        booking.communityId().equals(community)))
                .toList();
        return new RoomOccupancy(room.id(), date, slots);
    }
}

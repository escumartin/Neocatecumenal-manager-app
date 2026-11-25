package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import java.time.LocalDate;
import java.util.List;

public interface GetRoomOccupancy {

    RoomOccupancy get(Actor actor, RoomId roomId, LocalDate date, CommunityId requestedCommunity);

    record RoomOccupancy(RoomId roomId, LocalDate date, List<OccupiedSlot> occupied) {

        public RoomOccupancy {
            occupied = List.copyOf(occupied);
        }
    }

    record OccupiedSlot(TimeSlot slot, BookingStatus status, boolean ownCommunity) {
    }
}

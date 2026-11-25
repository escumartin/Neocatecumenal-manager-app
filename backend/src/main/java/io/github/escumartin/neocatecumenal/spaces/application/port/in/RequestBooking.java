package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;
import java.time.LocalDate;

public interface RequestBooking {

    BookingRequest request(Actor actor, RoomId roomId, CommunityId requestedCommunity, LocalDate date,
            TimeSlot slot, Reason reason);

    BookingRequest request(Actor actor, RoomId roomId, CommunityId requestedCommunity, LocalDate date, TimeSlot slot,
            Reason reason, WeeklyRecurrence recurrence);
}

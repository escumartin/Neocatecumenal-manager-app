package io.github.escumartin.neocatecumenal.agenda.application.port.in;

import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import java.time.LocalDate;

public interface CreateEvent {

    EventDetails create(Actor actor, CommunityId requestedCommunity, EventType type, EventTitle title, LocalDate date,
            TimeSlot slot, RoomId roomId);
}

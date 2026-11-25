package io.github.escumartin.neocatecumenal.agenda.application.port.in;

import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;

public interface GetEvent {

    EventDetails get(Actor actor, EventId id);
}

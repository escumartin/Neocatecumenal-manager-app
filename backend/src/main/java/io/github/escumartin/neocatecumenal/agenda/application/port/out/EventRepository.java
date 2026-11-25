package io.github.escumartin.neocatecumenal.agenda.application.port.out;

import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import java.time.LocalDate;
import java.util.Optional;

public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(EventId id);

    int anonymize(UserId userId);

    int deleteWithDateBefore(LocalDate date);
}

package io.github.escumartin.neocatecumenal.agenda.application;

import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryEvents implements EventRepository {

    final Map<EventId, Event> stored = new LinkedHashMap<>();

    @Override
    public Event save(Event event) {
        stored.put(event.id(), event);
        return event;
    }

    @Override
    public Optional<Event> findById(EventId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public boolean existsByCommunity(CommunityId communityId) {
        return stored.values().stream().anyMatch(event -> event.communityId().equals(communityId));
    }

    @Override
    public int anonymize(UserId userId) {
        int affected = 0;
        for (Event event : List.copyOf(stored.values())) {
            if (event.createdBy().filter(userId::equals).isPresent()) {
                affected++;
                stored.put(event.id(), Event.restore(event.id(), event.communityId(), event.type(), event.title(),
                        event.date(), event.slot(), event.roomId(), event.bookingRequestId(), null,
                        event.createdAt()));
            }
        }
        return affected;
    }

    @Override
    public int deleteWithDateBefore(LocalDate date) {
        int before = stored.size();
        stored.values().removeIf(event -> event.date().isBefore(date));
        return before - stored.size();
    }
}

package io.github.escumartin.neocatecumenal.agenda.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventPersistenceAdapter implements EventRepository {

    private final EventSpringDataRepository repository;

    EventPersistenceAdapter(EventSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Event save(Event event) {
        repository.saveAndFlush(toEntity(event));
        return event;
    }

    @Override
    public Optional<Event> findById(EventId id) {
        return repository.findById(id.value()).map(EventPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public int anonymize(UserId userId) {
        return repository.clearCreator(userId.value());
    }

    @Override
    @Transactional
    public int deleteWithDateBefore(LocalDate date) {
        return repository.deleteWithDateBefore(date);
    }

    private static EventEntity toEntity(Event event) {
        EventEntity entity = new EventEntity();
        entity.id = event.id().value();
        entity.communityId = event.communityId().value();
        entity.type = event.type();
        entity.title = event.title().value();
        entity.eventDate = event.date();
        entity.startTime = event.slot().start();
        entity.endTime = event.slot().end();
        entity.roomId = event.roomId().value();
        entity.bookingRequestId = event.bookingRequestId().value();
        entity.createdBy = event.createdBy().map(UserId::value).orElse(null);
        entity.createdAt = event.createdAt();
        return entity;
    }

    private static Event toDomain(EventEntity entity) {
        return Event.restore(new EventId(entity.id), new CommunityId(entity.communityId), entity.type,
                new EventTitle(entity.title), entity.eventDate, new TimeSlot(entity.startTime, entity.endTime),
                new RoomId(entity.roomId), new BookingRequestId(entity.bookingRequestId),
                entity.createdBy == null ? null : new UserId(entity.createdBy), entity.createdAt);
    }
}

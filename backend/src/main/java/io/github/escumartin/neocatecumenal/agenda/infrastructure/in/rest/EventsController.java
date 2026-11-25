package io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.CreateEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.GetEvent;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.generated.api.EventsApi;
import io.github.escumartin.neocatecumenal.generated.model.Event;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EventsController implements EventsApi {

    private final ActorResolver actors;
    private final CreateEvent createEvent;
    private final GetEvent getEvent;

    EventsController(ActorResolver actors, CreateEvent createEvent, GetEvent getEvent) {
        this.actors = actors;
        this.createEvent = createEvent;
        this.getEvent = getEvent;
    }

    @Override
    public ResponseEntity<Event> createEvent(io.github.escumartin.neocatecumenal.generated.model.CreateEvent body) {
        Actor actor = actors.currentActor();
        CommunityId community = actors.requestedCommunity(actor, body.getCommunityId()).orElse(null);
        var created = createEvent.create(actor, community, EventsMapper.toType(body.getType()),
                EventsMapper.toTitle(body.getTitle()), body.getDate(),
                EventsMapper.toSlot(body.getStartTime(), body.getEndTime()), new RoomId(body.getRoomId()));
        return ResponseEntity.created(URI.create("/api/events/" + created.event().id().value()))
                .body(EventsMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<Event> getEvent(UUID eventId) {
        var found = getEvent.get(actors.currentActor(), new EventId(eventId));
        return ResponseEntity.ok(EventsMapper.toResponse(found));
    }
}

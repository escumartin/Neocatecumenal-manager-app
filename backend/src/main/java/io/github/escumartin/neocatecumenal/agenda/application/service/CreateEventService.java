package io.github.escumartin.neocatecumenal.agenda.application.service;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.CreateEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaFieldInvalid;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Conflict;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventBookingStatus;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventPolicy;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Booked;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Occupied;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.RoomUnavailable;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CreateEventService implements CreateEvent {

    private static final Logger log = LoggerFactory.getLogger(CreateEventService.class);

    private final SpacesApi spaces;
    private final EventRepository events;
    private final CommunityLock communities;
    private final Clock clock;

    public CreateEventService(SpacesApi spaces, EventRepository events, CommunityLock communities, Clock clock) {
        this.spaces = spaces;
        this.events = events;
        this.communities = communities;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventDetails create(Actor actor, CommunityId requestedCommunity, EventType type, EventTitle title,
            LocalDate date, TimeSlot slot, RoomId roomId) {
        CommunityId community = EventPolicy.actingCommunity(actor, requestedCommunity);
        LocalDateTime now = LocalDateTime.now(clock);
        Event.requireStartInFuture(date, slot, now);
        if (!communities.lockForShare(community)) {
            throw new AgendaFieldInvalid("communityId", "COMMUNITY_NOT_FOUND");
        }
        EventId id = new EventId(UUID.randomUUID());
        EventBookingResult result = spaces.requestForEvent(new EventBookingCommand(id.value(), roomId.value(),
                community.value(), actor.userId().value(), date, slot.start(), slot.end(), title.value()));
        return switch (result) {
            case Booked booked -> {
                Event saved = events.save(Event.create(id, actor, community, type, title, date, slot, roomId,
                        new BookingRequestId(booked.bookingRequestId()), now, clock.instant()));
                log.atInfo().addKeyValue("eventId", saved.id().value()).log("Evento registrado con su solicitud");
                yield new EventDetails(saved, EventBookingStatus.PENDING);
            }
            case Occupied occupied -> {
                log.atInfo().addKeyValue("conflicts", occupied.conflicts().size())
                        .log("Evento rechazado por ocupación del salón");
                throw new RoomOccupied(occupied.conflicts().stream()
                        .map(conflict -> new Conflict(conflict.date(),
                                new TimeSlot(conflict.startTime(), conflict.endTime())))
                        .toList());
            }
            case RoomUnavailable unavailable -> {
                log.atInfo().addKeyValue("reason", unavailable.reason()).log("Evento rechazado por salón no disponible");
                throw switch (unavailable.reason()) {
                    case NOT_FOUND -> new RoomNotFound();
                    case INACTIVE -> new RoomInactive();
                };
            }
        };
    }
}

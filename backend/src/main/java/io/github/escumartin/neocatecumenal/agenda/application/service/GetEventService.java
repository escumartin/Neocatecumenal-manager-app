package io.github.escumartin.neocatecumenal.agenda.application.service;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.GetEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventBookingStatus;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.EventPolicy;
import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import org.springframework.transaction.annotation.Transactional;

public class GetEventService implements GetEvent {

    private final SpacesApi spaces;
    private final EventRepository events;

    public GetEventService(SpacesApi spaces, EventRepository events) {
        this.spaces = spaces;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetails get(Actor actor, EventId id) {
        EventPolicy.requireStaff(actor);
        Event event = events.findById(id).orElseThrow(EventNotFound::new);
        if (!EventPolicy.canSee(actor, event.communityId())) {
            throw new EventNotFound();
        }
        BookingRequestState state = spaces.bookingRequestStatus(event.bookingRequestId().value())
                .orElseThrow(EventNotFound::new);
        return new EventDetails(event, toStatus(state));
    }

    private static EventBookingStatus toStatus(BookingRequestState state) {
        return switch (state) {
            case PENDING -> EventBookingStatus.PENDING;
            case APPROVED -> EventBookingStatus.APPROVED;
            case REJECTED -> EventBookingStatus.REJECTED;
            case CANCELLED -> EventBookingStatus.CANCELLED;
        };
    }
}

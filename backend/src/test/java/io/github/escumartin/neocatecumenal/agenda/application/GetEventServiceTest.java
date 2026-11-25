package io.github.escumartin.neocatecumenal.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.application.service.GetEventService;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.ActorRole;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaForbidden;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventBookingStatus;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetEventServiceTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private final FakeSpaces spaces = new FakeSpaces();
    private final InMemoryEvents events = new InMemoryEvents();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);

    private GetEventService service;
    private Event ownEvent;
    private Event otherEvent;

    @BeforeEach
    void setUp() {
        service = new GetEventService(spaces, events);
        ownEvent = stored(OWN);
        otherEvent = stored(OTHER);
    }

    private Event stored(CommunityId community) {
        BookingRequestId request = new BookingRequestId(UUID.randomUUID());
        spaces.states.put(request.value(), BookingRequestState.PENDING);
        return events.save(Event.restore(new EventId(UUID.randomUUID()), community, EventType.CONVIVENCE,
                new EventTitle("Day trip"), LocalDate.of(2030, 5, 11),
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(18, 0)), new RoomId(UUID.randomUUID()), request,
                new UserId(UUID.randomUUID()), Instant.parse("2030-05-10T08:30:00Z")));
    }

    @Test
    void aModeratorSeesAnEventOfTheirCommunityWithItsRequestStatus() {
        EventDetails details = service.get(moderator, ownEvent.id());

        assertThat(details.event()).isEqualTo(ownEvent);
        assertThat(details.bookingStatus()).isEqualTo(EventBookingStatus.PENDING);
    }

    @Test
    void aModeratorGetsNotFoundForAnEventOfAnotherCommunity() {
        assertThatThrownBy(() -> service.get(moderator, otherEvent.id())).isInstanceOf(EventNotFound.class);
    }

    @Test
    void anAdministratorSeesAnyEvent() {
        assertThat(service.get(administrator, otherEvent.id()).event()).isEqualTo(otherEvent);
    }

    @Test
    void aRegisteredUserIsForbiddenEvenForAnUnknownEvent() {
        assertThatThrownBy(() -> service.get(registered, new EventId(UUID.randomUUID())))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void aRegisteredUserCannotSeeEvents() {
        assertThatThrownBy(() -> service.get(registered, ownEvent.id())).isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void theStatusIsTheCurrentOneAfterTheRequestIsRejected() {
        spaces.states.put(ownEvent.bookingRequestId().value(), BookingRequestState.REJECTED);

        assertThat(service.get(moderator, ownEvent.id()).bookingStatus()).isEqualTo(EventBookingStatus.REJECTED);
    }

    @Test
    void theStatusIsTheCurrentOneAfterTheRequestIsCancelled() {
        spaces.states.put(ownEvent.bookingRequestId().value(), BookingRequestState.CANCELLED);

        assertThat(service.get(moderator, ownEvent.id()).bookingStatus()).isEqualTo(EventBookingStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(BookingRequestState.class)
    void everyRequestStateIsMappedToTheEventBookingStatusOfTheSameName(BookingRequestState state) {
        spaces.states.put(ownEvent.bookingRequestId().value(), state);

        assertThat(service.get(moderator, ownEvent.id()).bookingStatus().name()).isEqualTo(state.name());
    }

    @Test
    void anEventWhoseRequestWasPurgedIsNotFound() {
        spaces.states.remove(ownEvent.bookingRequestId().value());

        assertThatThrownBy(() -> service.get(moderator, ownEvent.id())).isInstanceOf(EventNotFound.class);
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> service.get(administrator, new EventId(UUID.randomUUID())))
                .isInstanceOf(EventNotFound.class);
    }
}

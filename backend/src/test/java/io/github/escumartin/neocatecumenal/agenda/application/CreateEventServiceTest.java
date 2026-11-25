package io.github.escumartin.neocatecumenal.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.application.service.CreateEventService;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.ActorRole;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaFieldInvalid;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaForbidden;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventBookingStatus;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingConflict;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateEventServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());
    private static final EventTitle TITLE = new EventTitle("Opening word");

    private final FakeSpaces spaces = new FakeSpaces();
    private final InMemoryEvents events = new InMemoryEvents();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);

    private CreateEventService service;

    @BeforeEach
    void setUp() {
        service = new CreateEventService(spaces, events, CLOCK);
    }

    private EventDetails create(Actor actor, CommunityId community) {
        return service.create(actor, community, EventType.WORD, TITLE, TOMORROW, SLOT, ROOM);
    }

    @Test
    void aBookedRoomSavesTheEventWithItsBookingRequest() {
        EventDetails created = create(moderator, null);

        Event saved = events.stored.get(created.event().id());
        assertThat(saved).isNotNull();
        assertThat(saved.bookingRequestId()).isEqualTo(new BookingRequestId(spaces.nextBookingRequestId));
        assertThat(saved.communityId()).isEqualTo(OWN);
        assertThat(saved.type()).isEqualTo(EventType.WORD);
        assertThat(saved.title()).isEqualTo(TITLE);
        assertThat(saved.date()).isEqualTo(TOMORROW);
        assertThat(saved.slot()).isEqualTo(SLOT);
        assertThat(saved.roomId()).isEqualTo(ROOM);
        assertThat(saved.createdBy()).contains(moderator.userId());
        assertThat(saved.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(created.bookingStatus()).isEqualTo(EventBookingStatus.PENDING);
    }

    @Test
    void theBookingIsRequestedWithTheEventDataAndTheTitleAsReason() {
        EventDetails created = create(moderator, null);

        assertThat(spaces.commands).containsExactly(new EventBookingCommand(created.event().id().value(),
                ROOM.value(), OWN.value(), moderator.userId().value(), TOMORROW, SLOT.start(), SLOT.end(),
                TITLE.value()));
    }

    @Test
    void anAdministratorCreatesTheEventForTheChosenCommunity() {
        EventDetails created = create(administrator, OTHER);

        assertThat(created.event().communityId()).isEqualTo(OTHER);
        assertThat(spaces.commands.get(0).communityId()).isEqualTo(OTHER.value());
    }

    @Test
    void anOccupiedRoomRaisesRoomOccupiedWithTheConflictsAndSavesNothing() {
        spaces.result = new EventBookingResult.Occupied(List.of(
                new EventBookingConflict(TOMORROW, LocalTime.of(17, 0), LocalTime.of(19, 0))));

        assertThatThrownBy(() -> create(moderator, null)).isInstanceOfSatisfying(RoomOccupied.class, failure -> {
            assertThat(failure.conflicts()).hasSize(1);
            assertThat(failure.conflicts().get(0).date()).isEqualTo(TOMORROW);
            assertThat(failure.conflicts().get(0).slot())
                    .isEqualTo(new TimeSlot(LocalTime.of(17, 0), LocalTime.of(19, 0)));
        });
        assertThat(events.stored).isEmpty();
    }

    @Test
    void anUnknownRoomRaisesRoomNotFoundAndSavesNothing() {
        spaces.result = new EventBookingResult.RoomUnavailable(EventBookingResult.RoomUnavailable.Reason.NOT_FOUND);

        assertThatThrownBy(() -> create(moderator, null)).isInstanceOf(RoomNotFound.class);
        assertThat(events.stored).isEmpty();
    }

    @Test
    void anInactiveRoomRaisesRoomInactiveAndSavesNothing() {
        spaces.result = new EventBookingResult.RoomUnavailable(EventBookingResult.RoomUnavailable.Reason.INACTIVE);

        assertThatThrownBy(() -> create(moderator, null)).isInstanceOf(RoomInactive.class);
        assertThat(events.stored).isEmpty();
    }

    @Test
    void aModeratorCannotCreateForAnotherCommunity() {
        assertThatThrownBy(() -> create(moderator, OTHER)).isInstanceOf(AgendaForbidden.class);
        assertThat(spaces.commands).isEmpty();
        assertThat(events.stored).isEmpty();
    }

    @Test
    void aRegisteredUserCannotCreate() {
        assertThatThrownBy(() -> create(registered, null)).isInstanceOf(AgendaForbidden.class);
        assertThat(spaces.commands).isEmpty();
    }

    @Test
    void anAdministratorMustChooseACommunity() {
        assertThatThrownBy(() -> create(administrator, null))
                .isInstanceOfSatisfying(AgendaFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("COMMUNITY_REQUIRED"));
        assertThat(spaces.commands).isEmpty();
        assertThat(events.stored).isEmpty();
    }

    @Test
    void aStartInThePastIsRejectedBeforeAskingSpaces() {
        assertThatThrownBy(() -> service.create(moderator, null, EventType.WORD, TITLE, LocalDate.of(2030, 5, 10),
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0)), ROOM))
                .isInstanceOfSatisfying(AgendaFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE"));
        assertThat(spaces.commands).isEmpty();
    }
}

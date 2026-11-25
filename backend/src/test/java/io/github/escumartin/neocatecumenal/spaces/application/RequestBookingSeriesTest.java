package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.RoomServicesTest.InMemoryRooms;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestBookingService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestBookingSeriesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate FIRST = LocalDate.of(2030, 5, 14);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);

    private RequestBookingService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new RequestBookingService(rooms, requests, CLOCK);
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    private BookingRequest requestSeries(TimeSlot slot, int weeks) {
        return service.request(moderator, room.id(), null, FIRST, slot, new Reason("Catechesis"),
                new WeeklyRecurrence(FIRST.plusWeeks(weeks - 1)));
    }

    @Test
    void aSeriesWithoutConflictsStoresEveryOccurrence() {
        BookingRequest created = requestSeries(SLOT, 8);

        assertThat(created.bookings()).hasSize(8);
        assertThat(requests.stored.get(created.id()).bookings()).hasSize(8);
        assertThat(requests.stored.get(created.id()).recurrence()).isPresent();
    }

    @Test
    void aConflictingOccurrenceRejectsTheWholeSeriesAndStoresNothing() {
        service.request(moderator, room.id(), null, FIRST.plusWeeks(3), SLOT, new Reason("Rehearsal"));

        assertThatThrownBy(() -> requestSeries(new TimeSlot(LocalTime.of(19, 0), LocalTime.of(21, 0)), 8))
                .isInstanceOfSatisfying(RoomOccupied.class, failure -> assertThat(failure.conflicts())
                        .containsExactly(new Conflict(FIRST.plusWeeks(3), SLOT)));
        assertThat(requests.stored).hasSize(1);
    }

    @Test
    void everyConflictingDateIsReported() {
        service.request(moderator, room.id(), null, FIRST.plusWeeks(1), SLOT, new Reason("Rehearsal"));
        service.request(moderator, room.id(), null, FIRST.plusWeeks(5), SLOT, new Reason("Rehearsal"));

        assertThatThrownBy(() -> requestSeries(SLOT, 8)).isInstanceOfSatisfying(RoomOccupied.class,
                failure -> assertThat(failure.conflicts()).extracting(Conflict::date)
                        .containsExactly(FIRST.plusWeeks(1), FIRST.plusWeeks(5)));
        assertThat(requests.stored).hasSize(2);
    }

    @Test
    void aSeriesTooLongIsRejectedAndStoresNothing() {
        assertThatThrownBy(() -> service.request(moderator, room.id(), null, FIRST, SLOT, new Reason("Catechesis"),
                new WeeklyRecurrence(FIRST.plusYears(1).plusDays(1))))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        invalid -> assertThat(invalid.code()).isEqualTo("RECURRENCE_TOO_LONG"));
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void theOccurrencesOfASeriesBlockLaterRequests() {
        requestSeries(SLOT, 4);

        assertThatThrownBy(() -> service.request(moderator, room.id(), null, FIRST.plusWeeks(2), SLOT,
                new Reason("Rehearsal"))).isInstanceOf(RoomOccupied.class);
    }

    @Test
    void aSeriesOfOneWeekIsASingleOccurrence() {
        List<Booking> bookings = requestSeries(SLOT, 1).bookings();

        assertThat(bookings).hasSize(1);
    }
}

package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.RoomServicesTest.InMemoryRooms;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestBookingService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestBookingConflictTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot EXISTING = slot(18, 0, 20, 0);
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);

    private RequestBookingService service;
    private Room room;

    private static TimeSlot slot(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeSlot(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }

    @BeforeEach
    void setUp() {
        service = new RequestBookingService(rooms, requests, CLOCK);
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
        service.request(moderator, room.id(), null, TOMORROW, EXISTING, new Reason("Catechesis"));
    }

    private BookingRequest requestFor(Room target, TimeSlot slot) {
        return service.request(moderator, target.id(), null, TOMORROW, slot, new Reason("Rehearsal"));
    }

    @Test
    void anOverlappingSlotIsRejectedWithTheConflictAndStoresNothing() {
        assertThatThrownBy(() -> requestFor(room, slot(19, 0, 21, 0)))
                .isInstanceOfSatisfying(RoomOccupied.class,
                        failure -> assertThat(failure.conflicts()).containsExactly(new Conflict(TOMORROW, EXISTING)));
        assertThat(requests.stored).hasSize(1);
    }

    @Test
    void aSlotStartingBeforeAndEndingInsideIsRejected() {
        assertThatThrownBy(() -> requestFor(room, slot(17, 0, 18, 30))).isInstanceOf(RoomOccupied.class);
    }

    @Test
    void aSlotEnvelopingTheExistingOneIsRejected() {
        assertThatThrownBy(() -> requestFor(room, slot(17, 0, 21, 0))).isInstanceOf(RoomOccupied.class);
    }

    @Test
    void contiguousSlotsAreAccepted() {
        requestFor(room, slot(20, 0, 21, 0));
        requestFor(room, slot(16, 0, 18, 0));

        assertThat(requests.stored).hasSize(3);
    }

    @Test
    void anotherRoomIsAccepted() {
        Room other = rooms.save(Room.create(administrator, new RoomName("Small hall")));

        requestFor(other, EXISTING);

        assertThat(requests.stored).hasSize(2);
    }

    @Test
    void anotherDayIsAccepted() {
        service.request(moderator, room.id(), null, TOMORROW.plusDays(1), EXISTING, new Reason("Rehearsal"));

        assertThat(requests.stored).hasSize(2);
    }

    @Test
    void rejectedAndCancelledOccurrencesDoNotBlockTheSlot() {
        requests.stored.clear();
        storeWithStatus(BookingStatus.REJECTED);
        storeWithStatus(BookingStatus.CANCELLED);

        requestFor(room, EXISTING);

        assertThat(requests.stored).hasSize(3);
    }

    @Test
    void approvedOccurrencesBlockTheSlot() {
        requests.stored.clear();
        storeWithStatus(BookingStatus.APPROVED);

        assertThatThrownBy(() -> requestFor(room, EXISTING)).isInstanceOf(RoomOccupied.class);
    }

    private void storeWithStatus(BookingStatus status) {
        Booking booking = new Booking(new BookingId(UUID.randomUUID()), TOMORROW, EXISTING, status);
        StatusChange created = new StatusChange(null, null, BookingRequestStatus.PENDING, moderator.userId(),
                CLOCK.instant());
        BookingRequest stored = BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), room.id(), OWN,
                moderator.userId(), new Reason("Old"), null, TOMORROW, TOMORROW, EXISTING,
                BookingRequestStatus.PENDING, List.of(booking), List.of(created));
        requests.save(stored);
    }
}

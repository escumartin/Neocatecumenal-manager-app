package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingConflict;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Booked;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Occupied;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.RoomUnavailable;
import io.github.escumartin.neocatecumenal.spaces.application.RoomServicesTest.InMemoryRooms;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestForEventService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.EventId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpacesApiTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final Actor ADMINISTRATOR = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final UUID creator = UUID.randomUUID();

    private RequestForEventService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new RequestForEventService(rooms, requests, CLOCK);
        room = rooms.save(Room.create(ADMINISTRATOR, new RoomName("Main hall")));
    }

    private EventBookingCommand command(UUID eventId, UUID roomId, LocalTime start, LocalTime end) {
        return new EventBookingCommand(eventId, roomId, OWN.value(), creator, TOMORROW, start, end, "Opening word");
    }

    private EventBookingCommand defaultCommand(UUID eventId) {
        return command(eventId, room.id().value(), SLOT.start(), SLOT.end());
    }

    @Test
    void aFreeRoomIsBookedAsAPendingRequestLinkedToTheEvent() {
        UUID eventId = UUID.randomUUID();

        EventBookingResult result = service.requestForEvent(defaultCommand(eventId));

        assertThat(result).isInstanceOf(Booked.class);
        BookingRequestId id = new BookingRequestId(((Booked) result).bookingRequestId());
        BookingRequest stored = requests.stored.get(id);
        assertThat(stored.eventId()).contains(new EventId(eventId));
        assertThat(stored.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(stored.communityId()).isEqualTo(OWN);
        assertThat(stored.requestedBy()).contains(new UserId(creator));
        assertThat(stored.roomId()).isEqualTo(room.id());
        assertThat(stored.firstDate()).isEqualTo(TOMORROW);
        assertThat(stored.slot()).isEqualTo(SLOT);
        assertThat(stored.bookings()).hasSize(1);
    }

    @Test
    void theReasonOfTheRequestIsTheEventTitle() {
        Booked booked = (Booked) service.requestForEvent(defaultCommand(UUID.randomUUID()));

        assertThat(requests.stored.get(new BookingRequestId(booked.bookingRequestId())).reason())
                .isEqualTo(new Reason("Opening word"));
    }

    @Test
    void locksTheRoomForShareWhileBooking() {
        service.requestForEvent(defaultCommand(UUID.randomUUID()));

        assertThat(rooms.lockedForShare).containsExactly(room.id());
    }

    @Test
    void anOccupiedSlotReportsItsConflictsAndStoresNothing() {
        service.requestForEvent(defaultCommand(UUID.randomUUID()));

        EventBookingResult result = service.requestForEvent(command(UUID.randomUUID(), room.id().value(),
                LocalTime.of(19, 0), LocalTime.of(21, 0)));

        assertThat(result).isInstanceOf(Occupied.class);
        assertThat(((Occupied) result).conflicts()).containsExactly(
                new EventBookingConflict(TOMORROW, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        assertThat(requests.stored).hasSize(1);
    }

    @Test
    void aSlotLostToAConcurrentRequestEscapesAsRoomOccupiedInsteadOfBeingSwallowed() {
        RaceLostRequests racing = new RaceLostRequests();
        RequestForEventService racingService = new RequestForEventService(rooms, racing, CLOCK);

        assertThatThrownBy(() -> racingService.requestForEvent(defaultCommand(UUID.randomUUID())))
                .isInstanceOf(RoomOccupied.class);
    }

    @Test
    void anUnknownRoomIsUnavailableAsNotFound() {
        EventBookingResult result = service.requestForEvent(command(UUID.randomUUID(), UUID.randomUUID(),
                SLOT.start(), SLOT.end()));

        assertThat(result).isEqualTo(new RoomUnavailable(RoomUnavailable.Reason.NOT_FOUND));
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void anInactiveRoomIsUnavailableAsInactive() {
        rooms.save(Room.restore(room.id(), room.name(), false));

        EventBookingResult result = service.requestForEvent(defaultCommand(UUID.randomUUID()));

        assertThat(result).isEqualTo(new RoomUnavailable(RoomUnavailable.Reason.INACTIVE));
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void theStatusOfAnExistingRequestIsReported() {
        Booked booked = (Booked) service.requestForEvent(defaultCommand(UUID.randomUUID()));

        assertThat(service.bookingRequestStatus(booked.bookingRequestId())).contains(BookingRequestState.PENDING);
    }

    @Test
    void theStatusFollowsTheRequestAfterItIsRejected() {
        Booked booked = (Booked) service.requestForEvent(defaultCommand(UUID.randomUUID()));
        BookingRequestId id = new BookingRequestId(booked.bookingRequestId());
        BookingRequest request = requests.stored.get(id);
        request.reject(ADMINISTRATOR, null, CLOCK.instant());
        requests.save(request);

        assertThat(service.bookingRequestStatus(booked.bookingRequestId())).contains(BookingRequestState.REJECTED);
    }

    @Test
    void theStatusOfAnUnknownRequestIsEmpty() {
        assertThat(service.bookingRequestStatus(UUID.randomUUID())).isEmpty();
    }

    private static final class RaceLostRequests implements BookingRequestRepository {

        @Override
        public BookingRequest save(BookingRequest request) {
            throw new RoomOccupied(List.of());
        }

        @Override
        public Optional<BookingRequest> findById(BookingRequestId id) {
            return Optional.empty();
        }

        @Override
        public List<Conflict> findConflicts(RoomId roomId, Collection<LocalDate> dates, TimeSlot slot) {
            return List.of();
        }

        @Override
        public BookingRequestPage search(Optional<CommunityId> communityId, Set<BookingRequestStatus> statuses,
                int page, int size) {
            return new BookingRequestPage(List.of(), page, size, 0);
        }

        @Override
        public int anonymize(UserId userId) {
            return 0;
        }

        @Override
        public int deleteWithLastDateBefore(LocalDate date) {
            return 0;
        }
    }
}

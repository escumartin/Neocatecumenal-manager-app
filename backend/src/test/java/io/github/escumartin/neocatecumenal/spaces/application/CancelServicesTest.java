package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.service.CancelBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.CancelBookingService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestBookingService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingAlreadyStarted;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CancelServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 30);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY_A = new CommunityId(UUID.randomUUID());
    private static final CommunityId COMMUNITY_B = new CommunityId(UUID.randomUUID());

    private final RoomServicesTest.InMemoryRooms rooms = new RoomServicesTest.InMemoryRooms();
    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final FakeCommunityLock communityLock = new FakeCommunityLock();
    private final Actor moderatorA = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_A);
    private final Actor moderatorB = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_B);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY_A);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, COMMUNITY_A);

    private CancelBookingRequestService cancelRequest;
    private CancelBookingService cancelBooking;
    private BookingRequest approvedOfA;
    private RoomId room;

    @BeforeEach
    void setUp() {
        room = rooms.save(Room.create(administrator, new RoomName("Main hall"))).id();
        cancelRequest = new CancelBookingRequestService(requests, CLOCK);
        cancelBooking = new CancelBookingService(requests, CLOCK);
        approvedOfA = requests.save(approved(COMMUNITY_A, LocalDate.of(2030, 5, 12)));
    }

    private BookingRequest approved(CommunityId community, LocalDate date) {
        BookingRequest created = BookingRequest.request(moderatorA, room, community, date, SLOT,
                new Reason("Catechesis"), NOW, Instant.parse("2030-05-10T08:00:00Z"));
        created.approve(administrator, NOW, Instant.parse("2030-05-10T08:10:00Z"));
        return created;
    }

    private BookingRequest series(LocalDate... dates) {
        List<Booking> occurrences = Arrays.stream(dates)
                .map(date -> new Booking(new BookingId(UUID.randomUUID()), date, SLOT, BookingStatus.APPROVED))
                .toList();
        StatusChange created = new StatusChange(null, null, BookingRequestStatus.PENDING, moderatorA.userId(),
                Instant.parse("2030-04-01T08:00:00Z"));
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), room, COMMUNITY_A,
                moderatorA.userId(), new Reason("Catechesis"), null, dates[0], dates[dates.length - 1], SLOT,
                BookingRequestStatus.APPROVED, occurrences, List.of(created)));
    }

    @Test
    void aModeratorCancelsAnApprovedRequestOfTheirCommunity() {
        BookingRequest cancelled = cancelRequest.cancel(moderatorA, approvedOfA.id());

        assertThat(cancelled.status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(requests.stored.get(approvedOfA.id()).status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(requests.stored.get(approvedOfA.id()).bookings()).extracting(Booking::status)
                .containsOnly(BookingStatus.CANCELLED);
        assertThat(requests.stored.get(approvedOfA.id()).history()).hasSize(3);
    }

    @Test
    void aRegisteredUserCancellingAnUnknownRequestOrOccurrenceIsForbiddenNotNotFound() {
        BookingRequestId unknown = new BookingRequestId(UUID.randomUUID());

        assertThatThrownBy(() -> cancelRequest.cancel(registered, unknown)).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> cancelBooking.cancel(registered, unknown, new BookingId(UUID.randomUUID())))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void aModeratorCannotCancelARequestOfAnotherCommunity() {
        assertThatThrownBy(() -> cancelRequest.cancel(moderatorB, approvedOfA.id()))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(requests.stored.get(approvedOfA.id()).status()).isEqualTo(BookingRequestStatus.APPROVED);
    }

    @Test
    void anAdministratorCancelsARequestOfAnyCommunity() {
        BookingRequest ofB = requests.save(approved(COMMUNITY_B, LocalDate.of(2030, 5, 13)));

        cancelRequest.cancel(administrator, ofB.id());

        assertThat(requests.stored.get(ofB.id()).status()).isEqualTo(BookingRequestStatus.CANCELLED);
    }

    @Test
    void cancellingARequestWhoseSlotAlreadyStartedFails() {
        Clock afterStart = Clock.fixed(Instant.parse("2030-05-12T17:00:00Z"), ZoneId.of("Europe/Madrid"));

        assertThatThrownBy(() -> new CancelBookingRequestService(requests, afterStart).cancel(moderatorA,
                approvedOfA.id())).isInstanceOf(BookingAlreadyStarted.class);
        assertThat(requests.stored.get(approvedOfA.id()).status()).isEqualTo(BookingRequestStatus.APPROVED);
    }

    @Test
    void cancellingAnUnknownRequestIsNotFound() {
        BookingRequestId unknown = new BookingRequestId(UUID.randomUUID());

        assertThatThrownBy(() -> cancelRequest.cancel(administrator, unknown))
                .isInstanceOf(BookingRequestNotFound.class);
        assertThatThrownBy(() -> cancelBooking.cancel(administrator, unknown, new BookingId(UUID.randomUUID())))
                .isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void aCancelledSlotAcceptsANewRequest() {
        cancelRequest.cancel(moderatorA, approvedOfA.id());
        RequestBookingService request = new RequestBookingService(rooms, requests, communityLock, CLOCK);

        BookingRequest created = request.request(moderatorB, room, null, LocalDate.of(2030, 5, 12), SLOT,
                new Reason("Rehearsal"));

        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
    }

    @Test
    void cancellingASingleOccurrenceLeavesTheSeriesActive() {
        BookingRequest series = series(LocalDate.of(2030, 5, 12), LocalDate.of(2030, 5, 19));
        BookingId second = series.bookings().get(1).id();

        BookingRequest result = cancelBooking.cancel(moderatorA, series.id(), second);

        assertThat(result.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(requests.stored.get(series.id()).bookings()).extracting(Booking::status)
                .containsExactly(BookingStatus.APPROVED, BookingStatus.CANCELLED);
    }

    @Test
    void cancellingAnOccurrenceOfAnotherCommunityIsForbidden() {
        BookingId only = approvedOfA.bookings().get(0).id();

        assertThatThrownBy(() -> cancelBooking.cancel(moderatorB, approvedOfA.id(), only))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void cancellingAnOccurrenceThatStartedFails() {
        Clock afterStart = Clock.fixed(Instant.parse("2030-05-12T17:00:00Z"), ZoneId.of("Europe/Madrid"));
        BookingId only = approvedOfA.bookings().get(0).id();

        assertThatThrownBy(() -> new CancelBookingService(requests, afterStart).cancel(moderatorA, approvedOfA.id(),
                only)).isInstanceOf(BookingAlreadyStarted.class);
    }

    @Test
    void cancellingAnUnknownOccurrenceIsNotFound() {
        assertThatThrownBy(() -> cancelBooking.cancel(moderatorA, approvedOfA.id(),
                new BookingId(UUID.randomUUID()))).isInstanceOf(BookingRequestNotFound.class);
    }
}

package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.RoomServicesTest.InMemoryRooms;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestBookingService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestBookingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final FakeCommunityLock communityLock = new FakeCommunityLock();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);

    private RequestBookingService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new RequestBookingService(rooms, requests, communityLock, CLOCK);
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    @Test
    void aModeratorRequestsARoomForTheirCommunity() {
        BookingRequest created = service.request(moderator, room.id(), null, TOMORROW, SLOT, new Reason("Catechesis"));

        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(created.communityId()).isEqualTo(OWN);
        assertThat(created.requestedBy()).contains(moderator.userId());
        assertThat(created.history().get(0).changedAt()).isEqualTo(CLOCK.instant());
        assertThat(requests.stored).containsKey(created.id());
    }

    @Test
    void anAdministratorRequestsARoomForTheChosenCommunity() {
        BookingRequest created = service.request(administrator, room.id(), OTHER, TOMORROW, SLOT,
                new Reason("Catechesis"));

        assertThat(created.communityId()).isEqualTo(OTHER);
        assertThat(requests.stored.get(created.id()).communityId()).isEqualTo(OTHER);
    }

    @Test
    void theCommunityIsLockedForShareBeforeSaving() {
        service.request(administrator, room.id(), OTHER, TOMORROW, SLOT, new Reason("Catechesis"));

        assertThat(communityLock.locked).containsExactly(OTHER);
    }

    @Test
    void nothingIsSavedBeforeTheCommunityIsLocked() {
        communityLock.workDone = () -> !requests.stored.isEmpty();

        service.request(administrator, room.id(), OTHER, TOMORROW, SLOT, new Reason("Catechesis"));

        assertThat(communityLock.workDoneWhenLocked).containsExactly(false);
    }

    @Test
    void aCommunityDeletedMeanwhileIsRejectedAndNothingIsSaved() {
        communityLock.missing.add(OTHER);

        assertThatThrownBy(() -> service.request(administrator, room.id(), OTHER, TOMORROW, SLOT,
                new Reason("Catechesis")))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("communityId");
                    assertThat(failure.code()).isEqualTo("COMMUNITY_NOT_FOUND");
                });
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void anAdministratorMustChooseACommunity() {
        assertThatThrownBy(() -> service.request(administrator, room.id(), null, TOMORROW, SLOT,
                new Reason("Catechesis")))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("COMMUNITY_REQUIRED"));
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void aModeratorCannotRequestForAnotherCommunity() {
        assertThatThrownBy(() -> service.request(moderator, room.id(), OTHER, TOMORROW, SLOT,
                new Reason("Catechesis"))).isInstanceOf(SpacesForbidden.class);
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void aRegisteredUserCannotRequest() {
        assertThatThrownBy(() -> service.request(registered, room.id(), null, TOMORROW, SLOT,
                new Reason("Catechesis"))).isInstanceOf(SpacesForbidden.class);
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void anUnknownRoomIsNotFound() {
        assertThatThrownBy(() -> service.request(moderator, new RoomId(UUID.randomUUID()), null, TOMORROW, SLOT,
                new Reason("Catechesis"))).isInstanceOf(RoomNotFound.class);
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void anInactiveRoomCannotBeRequested() {
        rooms.save(Room.restore(room.id(), room.name(), false));

        assertThatThrownBy(() -> service.request(moderator, room.id(), null, TOMORROW, SLOT,
                new Reason("Catechesis"))).isInstanceOf(RoomInactive.class);
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void locksTheRoomForShareWhileRequesting() {
        service.request(moderator, room.id(), null, TOMORROW, SLOT, new Reason("Catechesis"));

        assertThat(rooms.lockedForShare).containsExactly(room.id());
    }

    @Test
    void aSlotAlreadyStartedTodayStoresNothing() {
        TimeSlot started = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.request(moderator, room.id(), null, LocalDate.of(2030, 5, 10), started,
                new Reason("Catechesis")))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE"));
        assertThat(requests.stored).isEmpty();
    }

    @Test
    void aSlotStartingWithinTheHourTodayIsAccepted() {
        TimeSlot soon = new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0));

        BookingRequest created = service.request(moderator, room.id(), null, LocalDate.of(2030, 5, 10), soon,
                new Reason("Catechesis"));

        assertThat(created.firstDate()).isEqualTo(LocalDate.of(2030, 5, 10));
    }
}

package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.service.ApproveBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ListBookingRequestsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RejectBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotPending;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class ResolveBookingRequestServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 30);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY_A = new CommunityId(UUID.randomUUID());
    private static final CommunityId COMMUNITY_B = new CommunityId(UUID.randomUUID());

    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final Actor moderatorA = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_A);
    private final Actor moderatorB = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_B);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY_A);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, COMMUNITY_A);

    private ApproveBookingRequestService approve;
    private RejectBookingRequestService reject;
    private BookingRequest requestOfA;
    private BookingRequest requestOfB;

    @BeforeEach
    void setUp() {
        approve = new ApproveBookingRequestService(requests, CLOCK);
        reject = new RejectBookingRequestService(requests, CLOCK);
        requestOfA = requests.save(create(moderatorA, COMMUNITY_A, LocalDate.of(2030, 5, 12)));
        requestOfB = requests.save(create(moderatorB, COMMUNITY_B, LocalDate.of(2030, 5, 11)));
    }

    private static BookingRequest create(Actor actor, CommunityId community, LocalDate date) {
        return BookingRequest.request(actor, new RoomId(UUID.randomUUID()), community, date, SLOT,
                new Reason("Catechesis"), NOW, Instant.parse("2030-05-10T08:00:00Z"));
    }

    @Test
    void anAdministratorApprovesARequestOfAnyCommunity() {
        BookingRequest approved = approve.approve(administrator, requestOfB.id());

        assertThat(approved.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(requests.stored.get(requestOfB.id()).status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(requests.stored.get(requestOfB.id()).bookings()).extracting(Booking::status)
                .containsOnly(BookingStatus.APPROVED);
        assertThat(requests.stored.get(requestOfB.id()).history()).hasSize(2);
    }

    @Test
    void anAdministratorRejectsWithAReasonAndItIsStored() {
        BookingRequest rejected = reject.reject(administrator, requestOfA.id(), new RejectionReason("Maintenance"));

        assertThat(rejected.status()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(requests.stored.get(requestOfA.id()).rejectionReason())
                .contains(new RejectionReason("Maintenance"));
        assertThat(requests.stored.get(requestOfA.id()).bookings()).extracting(Booking::status)
                .containsOnly(BookingStatus.REJECTED);
    }

    @Test
    void anAdministratorRejectsWithoutAReason() {
        reject.reject(administrator, requestOfA.id(), null);

        assertThat(requests.stored.get(requestOfA.id()).status()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(requests.stored.get(requestOfA.id()).rejectionReason()).isEmpty();
    }

    @Test
    void aModeratorCannotResolveEvenTheirOwnRequest() {
        assertThatThrownBy(() -> approve.approve(moderatorA, requestOfA.id())).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> reject.reject(moderatorA, requestOfA.id(), null))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(requests.stored.get(requestOfA.id()).status()).isEqualTo(BookingRequestStatus.PENDING);
    }

    @Test
    void aModeratorResolvingAnUnknownRequestIsForbiddenNotNotFound() {
        BookingRequestId unknown = new BookingRequestId(UUID.randomUUID());

        assertThatThrownBy(() -> approve.approve(moderatorA, unknown)).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> reject.reject(moderatorA, unknown, null)).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void aRegisteredUserCannotResolve() {
        assertThatThrownBy(() -> approve.approve(registered, requestOfA.id())).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> reject.reject(registered, requestOfA.id(), null))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void resolvingAnUnknownRequestIsNotFound() {
        BookingRequestId unknown = new BookingRequestId(UUID.randomUUID());

        assertThatThrownBy(() -> approve.approve(administrator, unknown)).isInstanceOf(BookingRequestNotFound.class);
        assertThatThrownBy(() -> reject.reject(administrator, unknown, null))
                .isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void resolvingAnAlreadyResolvedRequestIsNotPending() {
        approve.approve(administrator, requestOfA.id());

        assertThatThrownBy(() -> approve.approve(administrator, requestOfA.id()))
                .isInstanceOf(BookingRequestNotPending.class);
        assertThatThrownBy(() -> reject.reject(administrator, requestOfA.id(), null))
                .isInstanceOf(BookingRequestNotPending.class);
    }

    @Test
    void approvingARequestThatAlreadyStartedFailsButRejectingIsAllowed() {
        Clock afterStart = Clock.fixed(Instant.parse("2030-05-12T17:00:00Z"), ZoneId.of("Europe/Madrid"));

        assertThatThrownBy(() -> new ApproveBookingRequestService(requests, afterStart)
                .approve(administrator, requestOfA.id())).isInstanceOf(SpacesFieldInvalid.class);
        new RejectBookingRequestService(requests, afterStart).reject(administrator, requestOfA.id(), null);

        assertThat(requests.stored.get(requestOfA.id()).status()).isEqualTo(BookingRequestStatus.REJECTED);
    }

    @Test
    void anOptimisticLockFailureIsPropagatedUncaught() {
        BookingRequestRepository failing = mock(BookingRequestRepository.class);
        when(failing.findById(requestOfA.id())).thenAnswer(call -> requests.findById(requestOfA.id()));
        when(failing.save(any())).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> new ApproveBookingRequestService(failing, CLOCK)
                .approve(administrator, requestOfA.id())).isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> new RejectBookingRequestService(failing, CLOCK)
                .reject(administrator, requestOfA.id(), null)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void thePendingListOfAnAdministratorIncludesEveryCommunityAndDropsResolvedOnes() {
        ListBookingRequestsService list = new ListBookingRequestsService(requests);
        BookingRequest another = requests.save(create(moderatorB, COMMUNITY_B, LocalDate.of(2030, 5, 13)));
        approve.approve(administrator, another.id());

        var pending = list.list(administrator, null, Set.of(BookingRequestStatus.PENDING), 0, 20);

        assertThat(pending.items()).extracting(BookingRequest::id)
                .containsExactly(requestOfB.id(), requestOfA.id());
    }
}

package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.service.GetBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ListBookingRequestsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RectifyBookingReasonService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotPending;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingRequestQueriesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY_A = new CommunityId(UUID.randomUUID());
    private static final CommunityId COMMUNITY_B = new CommunityId(UUID.randomUUID());

    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final Actor moderatorA = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_A);
    private final Actor moderatorB = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_B);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY_A);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, COMMUNITY_A);

    private RectifyBookingReasonService rectify;
    private ListBookingRequestsService list;
    private GetBookingRequestService get;
    private BookingRequest requestOfA;
    private BookingRequest requestOfB;

    @BeforeEach
    void setUp() {
        rectify = new RectifyBookingReasonService(requests);
        list = new ListBookingRequestsService(requests);
        get = new GetBookingRequestService(requests);
        requestOfA = requests.save(create(moderatorA, COMMUNITY_A, LocalDate.of(2030, 5, 12)));
        requestOfB = requests.save(create(moderatorB, COMMUNITY_B, LocalDate.of(2030, 5, 11)));
    }

    private static BookingRequest create(Actor actor, CommunityId community, LocalDate date) {
        return BookingRequest.request(actor, new RoomId(UUID.randomUUID()), community, date, SLOT,
                new Reason("Catechesis"), NOW, Instant.parse("2030-05-10T08:00:00Z"));
    }

    private BookingRequest withStatus(BookingRequest request, BookingRequestStatus status) {
        return requests.save(BookingRequest.restore(request.id(), request.roomId(), request.communityId(),
                request.requestedBy().orElse(null), request.reason(),
                request.rejectionReason().orElse(null), request.firstDate(), request.lastDate(), request.slot(),
                status, request.bookings(), request.history()));
    }

    @Test
    void aModeratorRectifiesTheReasonOfTheirPendingRequest() {
        BookingRequest rectified = rectify.rectify(moderatorA, requestOfA.id(), new Reason("Youth catechesis"));

        assertThat(rectified.reason()).isEqualTo(new Reason("Youth catechesis"));
        assertThat(requests.stored.get(requestOfA.id()).reason()).isEqualTo(new Reason("Youth catechesis"));
    }

    @Test
    void anAdministratorRectifiesTheReasonOfAnyCommunity() {
        BookingRequest rectified = rectify.rectify(administrator, requestOfB.id(), new Reason("Youth catechesis"));

        assertThat(rectified.reason()).isEqualTo(new Reason("Youth catechesis"));
    }

    @Test
    void aModeratorCannotRectifyTheRequestOfAnotherCommunity() {
        assertThatThrownBy(() -> rectify.rectify(moderatorA, requestOfB.id(), new Reason("Youth catechesis")))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(requests.stored.get(requestOfB.id()).reason()).isEqualTo(new Reason("Catechesis"));
    }

    @Test
    void rectifyingAResolvedRequestIsRejected() {
        withStatus(requestOfA, BookingRequestStatus.APPROVED);

        assertThatThrownBy(() -> rectify.rectify(moderatorA, requestOfA.id(), new Reason("Youth catechesis")))
                .isInstanceOf(BookingRequestNotPending.class);
    }

    @Test
    void aRegisteredUserRectifyingAnUnknownRequestIsForbiddenNotNotFound() {
        assertThatThrownBy(() -> rectify.rectify(registered, new BookingRequestId(UUID.randomUUID()),
                new Reason("Youth catechesis"))).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void rectifyingAnUnknownRequestIsNotFound() {
        assertThatThrownBy(() -> rectify.rectify(moderatorA, new BookingRequestId(UUID.randomUUID()),
                new Reason("Youth catechesis"))).isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void aModeratorSeesTheRequestOfTheirCommunity() {
        assertThat(get.get(moderatorA, requestOfA.id()).id()).isEqualTo(requestOfA.id());
    }

    @Test
    void aModeratorGetsNotFoundForTheRequestOfAnotherCommunity() {
        assertThatThrownBy(() -> get.get(moderatorA, requestOfB.id())).isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void anAdministratorSeesAnyRequest() {
        assertThat(get.get(administrator, requestOfB.id()).id()).isEqualTo(requestOfB.id());
    }

    @Test
    void gettingAnUnknownRequestIsNotFound() {
        assertThatThrownBy(() -> get.get(moderatorA, new BookingRequestId(UUID.randomUUID())))
                .isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void aRegisteredUserCannotGetOrList() {
        assertThatThrownBy(() -> get.get(registered, requestOfA.id())).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> list.list(registered, null, Set.of(), 0, 20)).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void aModeratorListsOnlyTheirCommunity() {
        BookingRequestPage page = list.list(moderatorA, null, Set.of(), 0, 20);

        assertThat(page.items()).extracting(BookingRequest::id).containsExactly(requestOfA.id());
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    void aModeratorCannotListAnotherCommunity() {
        assertThatThrownBy(() -> list.list(moderatorA, COMMUNITY_B, Set.of(), 0, 20))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void anAdministratorListsEveryCommunityOrdersByFirstDateAndFiltersByCommunity() {
        assertThat(list.list(administrator, null, Set.of(), 0, 20).items())
                .extracting(BookingRequest::id).containsExactly(requestOfB.id(), requestOfA.id());
        assertThat(list.list(administrator, COMMUNITY_A, Set.of(), 0, 20).items())
                .extracting(BookingRequest::id).containsExactly(requestOfA.id());
    }

    @Test
    void listFiltersByStatus() {
        withStatus(requestOfB, BookingRequestStatus.APPROVED);

        assertThat(list.list(administrator, null, Set.of(BookingRequestStatus.APPROVED), 0, 20).items())
                .extracting(BookingRequest::id).containsExactly(requestOfB.id());
        assertThat(list.list(administrator, null, Set.of(BookingRequestStatus.PENDING), 0, 20).items())
                .extracting(BookingRequest::id).containsExactly(requestOfA.id());
    }

    @Test
    void listIsPaginated() {
        BookingRequestPage second = list.list(administrator, null, Set.of(), 1, 1);

        assertThat(second.items()).extracting(BookingRequest::id).containsExactly(requestOfA.id());
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(1);
        assertThat(second.totalItems()).isEqualTo(2);
    }
}

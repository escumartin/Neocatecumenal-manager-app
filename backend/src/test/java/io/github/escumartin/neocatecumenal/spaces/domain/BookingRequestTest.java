package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);

    private BookingRequest request(LocalDate date, TimeSlot slot) {
        return BookingRequest.request(moderator, ROOM, OWN, date, slot, new Reason("Catechesis"), NOW, AT);
    }

    private BookingRequest requestWithStatus(BookingRequestStatus status) {
        BookingRequest created = request(TOMORROW, SLOT);
        return BookingRequest.restore(created.id(), ROOM, OWN, moderator.userId(), new Reason("Catechesis"),
                null, TOMORROW, TOMORROW, SLOT, status, created.bookings(), created.history());
    }

    @Test
    void createsAPendingRequestForTheCommunityAndTheRequester() {
        BookingRequest created = request(TOMORROW, SLOT);

        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(created.roomId()).isEqualTo(ROOM);
        assertThat(created.communityId()).isEqualTo(OWN);
        assertThat(created.requestedBy()).contains(moderator.userId());
        assertThat(created.reason()).isEqualTo(new Reason("Catechesis"));
        assertThat(created.slot()).isEqualTo(SLOT);
    }

    @Test
    void createsASingleDatePendingOccurrence() {
        BookingRequest created = request(TOMORROW, SLOT);

        assertThat(created.firstDate()).isEqualTo(TOMORROW);
        assertThat(created.lastDate()).isEqualTo(TOMORROW);
        assertThat(created.bookings()).hasSize(1);
        Booking booking = created.bookings().get(0);
        assertThat(booking.date()).isEqualTo(TOMORROW);
        assertThat(booking.slot()).isEqualTo(SLOT);
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void recordsTheCreationInTheStatusHistory() {
        BookingRequest created = request(TOMORROW, SLOT);

        assertThat(created.history()).hasSize(1);
        StatusChange change = created.history().get(0);
        assertThat(change.bookingId()).isNull();
        assertThat(change.from()).isNull();
        assertThat(change.to()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(change.changedBy()).isEqualTo(moderator.userId());
        assertThat(change.changedAt()).isEqualTo(AT);
        assertThat(created.createdAt()).isEqualTo(AT);
    }

    @Test
    void generatesDistinctIdentifiers() {
        BookingRequest first = request(TOMORROW, SLOT);
        BookingRequest second = request(TOMORROW, SLOT);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.bookings().get(0).id()).isNotEqualTo(second.bookings().get(0).id());
    }

    @Test
    void rejectsADateInThePast() {
        assertThatThrownBy(() -> request(LocalDate.of(2030, 5, 9), SLOT))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("startTime");
                    assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE");
                });
    }

    @Test
    void rejectsATodaySlotThatAlreadyStarted() {
        TimeSlot started = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> request(NOW.toLocalDate(), started))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE"));
    }

    @Test
    void rejectsATodaySlotStartingExactlyNow() {
        TimeSlot startingNow = new TimeSlot(NOW.toLocalTime(), LocalTime.of(12, 0));

        assertThatThrownBy(() -> request(NOW.toLocalDate(), startingNow))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE"));
    }

    @Test
    void acceptsATodaySlotStartingWithinAnHour() {
        TimeSlot soon = new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0));

        BookingRequest created = request(NOW.toLocalDate(), soon);

        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
    }

    @Test
    void rectifiesTheReasonOfAPendingRequestWithoutTouchingTheHistory() {
        BookingRequest created = request(TOMORROW, SLOT);
        List<StatusChange> historyBefore = created.history();

        created.rectifyReason(moderator, new Reason("Youth catechesis"));

        assertThat(created.reason()).isEqualTo(new Reason("Youth catechesis"));
        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(created.history()).isEqualTo(historyBefore);
    }

    @Test
    void anAdministratorRectifiesTheReasonOfAnyCommunity() {
        Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OTHER);
        BookingRequest created = request(TOMORROW, SLOT);

        created.rectifyReason(administrator, new Reason("Youth catechesis"));

        assertThat(created.reason()).isEqualTo(new Reason("Youth catechesis"));
    }

    @Test
    void aModeratorOfAnotherCommunityCannotRectifyTheReason() {
        Actor stranger = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OTHER);
        BookingRequest created = request(TOMORROW, SLOT);

        assertThatThrownBy(() -> created.rectifyReason(stranger, new Reason("Youth catechesis")))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(created.reason()).isEqualTo(new Reason("Catechesis"));
    }

    @Test
    void aRegisteredUserCannotRectifyTheReason() {
        Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);
        BookingRequest created = request(TOMORROW, SLOT);

        assertThatThrownBy(() -> created.rectifyReason(registered, new Reason("Youth catechesis")))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void rectifyingARequestThatIsNotPendingIsRejected() {
        for (BookingRequestStatus status : List.of(BookingRequestStatus.APPROVED, BookingRequestStatus.REJECTED,
                BookingRequestStatus.CANCELLED)) {
            BookingRequest resolved = requestWithStatus(status);

            assertThatThrownBy(() -> resolved.rectifyReason(moderator, new Reason("Youth catechesis")))
                    .isInstanceOf(BookingRequestNotPending.class);
            assertThat(resolved.reason()).isEqualTo(new Reason("Catechesis"));
        }
    }

    @Test
    void checksPermissionBeforeTheStateWhenRectifying() {
        Actor stranger = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OTHER);
        BookingRequest approved = requestWithStatus(BookingRequestStatus.APPROVED);

        assertThatThrownBy(() -> approved.rectifyReason(stranger, new Reason("Youth catechesis")))
                .isInstanceOf(SpacesForbidden.class);
    }
}

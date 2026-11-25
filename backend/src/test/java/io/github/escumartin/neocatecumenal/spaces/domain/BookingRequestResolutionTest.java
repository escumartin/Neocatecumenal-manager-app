package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingRequestResolutionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant CREATED_AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2030-05-10T09:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);

    private BookingRequest pending() {
        return BookingRequest.request(moderator, ROOM, OWN, TOMORROW, SLOT, new Reason("Catechesis"), NOW,
                CREATED_AT);
    }

    private BookingRequest pendingSeries() {
        return BookingRequest.request(moderator, ROOM, OWN, TOMORROW, SLOT, new Reason("Catechesis"),
                new WeeklyRecurrence(TOMORROW.plusWeeks(3)), NOW, CREATED_AT);
    }

    private BookingRequest withStatus(BookingRequestStatus status) {
        BookingRequest created = pending();
        return BookingRequest.restore(created.id(), ROOM, OWN, moderator.userId(), new Reason("Catechesis"), null,
                TOMORROW, TOMORROW, SLOT, status, created.bookings(), created.history());
    }

    @Test
    void anAdministratorApprovesAPendingRequest() {
        BookingRequest request = pending();

        request.approve(administrator, NOW, RESOLVED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.APPROVED);
        assertThat(request.rejectionReason()).isEmpty();
    }

    @Test
    void approvingRecordsWhoAndWhenInTheHistory() {
        BookingRequest request = pending();

        request.approve(administrator, NOW, RESOLVED_AT);

        assertThat(request.history()).hasSize(2);
        assertThat(request.history().get(1)).isEqualTo(new StatusChange(null, BookingRequestStatus.PENDING,
                BookingRequestStatus.APPROVED, administrator.userId(), RESOLVED_AT));
    }

    @Test
    void anAdministratorRejectsAPendingRequestWithAReason() {
        BookingRequest request = pending();

        request.reject(administrator, new RejectionReason("Hall under maintenance"), RESOLVED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.REJECTED);
        assertThat(request.rejectionReason()).contains(new RejectionReason("Hall under maintenance"));
    }

    @Test
    void anAdministratorRejectsAPendingRequestWithoutAReason() {
        BookingRequest request = pending();

        request.reject(administrator, null, RESOLVED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEmpty();
    }

    @Test
    void rejectingRecordsWhoAndWhenInTheHistory() {
        BookingRequest request = pending();

        request.reject(administrator, null, RESOLVED_AT);

        assertThat(request.history()).hasSize(2);
        assertThat(request.history().get(1)).isEqualTo(new StatusChange(null, BookingRequestStatus.PENDING,
                BookingRequestStatus.REJECTED, administrator.userId(), RESOLVED_AT));
    }

    @Test
    void rejectingFreesTheSlotBecauseNoOccurrenceStaysActive() {
        BookingRequest request = pending();

        request.reject(administrator, null, RESOLVED_AT);

        assertThat(request.bookings()).noneMatch(booking -> booking.status().active());
    }

    @Test
    void aModeratorCannotApproveOrReject() {
        BookingRequest request = pending();

        assertThatThrownBy(() -> request.approve(moderator, NOW, RESOLVED_AT)).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> request.reject(moderator, null, RESOLVED_AT)).isInstanceOf(SpacesForbidden.class);
        assertThat(request.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void aRegisteredUserCannotApproveOrReject() {
        BookingRequest request = pending();

        assertThatThrownBy(() -> request.approve(registered, NOW, RESOLVED_AT)).isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> request.reject(registered, null, RESOLVED_AT)).isInstanceOf(SpacesForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(value = BookingRequestStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    void approvingOrRejectingAResolvedRequestFailsWithoutChangingIt(BookingRequestStatus status) {
        BookingRequest request = withStatus(status);

        assertThatThrownBy(() -> request.approve(administrator, NOW, RESOLVED_AT))
                .isInstanceOf(BookingRequestNotPending.class);
        assertThatThrownBy(() -> request.reject(administrator, null, RESOLVED_AT))
                .isInstanceOf(BookingRequestNotPending.class);
        assertThat(request.status()).isEqualTo(status);
        assertThat(request.history()).hasSize(1);
        assertThat(request.rejectionReason()).isEmpty();
    }

    @Test
    void approvingARequestWhoseFirstOccurrenceStartedFails() {
        BookingRequest request = pending();
        LocalDateTime afterStart = TOMORROW.atTime(18, 30);

        assertThatThrownBy(() -> request.approve(administrator, afterStart, RESOLVED_AT))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("startTime");
                    assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE");
                });
        assertThat(request.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.PENDING);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void approvingARequestAtTheExactStartInstantFails() {
        BookingRequest request = pending();

        assertThatThrownBy(() -> request.approve(administrator, TOMORROW.atTime(18, 0), RESOLVED_AT))
                .isInstanceOf(SpacesFieldInvalid.class);
    }

    @Test
    void rejectingARequestWhoseFirstOccurrenceStartedIsAllowed() {
        BookingRequest request = pending();

        request.reject(administrator, new RejectionReason("Nobody resolved it in time"), RESOLVED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.REJECTED);
    }

    @Test
    void onlyPendingOccurrencesChangeWhenApproving() {
        BookingRequest created = pending();
        Booking cancelled = new Booking(new BookingId(UUID.randomUUID()), TOMORROW.plusDays(7), SLOT,
                BookingStatus.CANCELLED);
        BookingRequest request = BookingRequest.restore(created.id(), ROOM, OWN, moderator.userId(),
                new Reason("Catechesis"), null, TOMORROW, TOMORROW.plusDays(7), SLOT, BookingRequestStatus.PENDING,
                Stream.concat(created.bookings().stream(), Stream.of(cancelled)).toList(), created.history());

        request.approve(administrator, NOW, RESOLVED_AT);

        assertThat(request.bookings()).extracting(Booking::status)
                .containsExactly(BookingStatus.APPROVED, BookingStatus.CANCELLED);
    }

    @Test
    void approvingASeriesApprovesEveryOccurrence() {
        BookingRequest created = pendingSeries();

        created.approve(administrator, NOW, RESOLVED_AT);

        assertThat(created.bookings()).hasSize(4).allMatch(booking -> booking.status() == BookingStatus.APPROVED);
        assertThat(created.status()).isEqualTo(BookingRequestStatus.APPROVED);
    }

    @Test
    void rejectingASeriesRejectsEveryOccurrence() {
        BookingRequest created = pendingSeries();

        created.reject(administrator, new RejectionReason("Not available"), RESOLVED_AT);

        assertThat(created.bookings()).hasSize(4).allMatch(booking -> booking.status() == BookingStatus.REJECTED);
        assertThat(created.status()).isEqualTo(BookingRequestStatus.REJECTED);
    }

    @Test
    void approvingASeriesWhoseFirstOccurrenceHasStartedIsRejectedButRejectingItIsAllowed() {
        BookingRequest created = pendingSeries();
        LocalDateTime afterStart = TOMORROW.atTime(18, 30);

        assertThatThrownBy(() -> created.approve(administrator, afterStart, RESOLVED_AT))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        invalid -> assertThat(invalid.code()).isEqualTo("START_NOT_IN_FUTURE"));
        created.reject(administrator, null, RESOLVED_AT);

        assertThat(created.status()).isEqualTo(BookingRequestStatus.REJECTED);
    }
}

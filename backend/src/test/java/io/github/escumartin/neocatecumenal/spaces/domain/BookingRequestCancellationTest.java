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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingRequestCancellationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant CANCELLED_AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2030, 5, 10);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor otherModerator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OTHER);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OTHER);

    private static Booking booking(LocalDate date, BookingStatus status) {
        return new Booking(new BookingId(UUID.randomUUID()), date, SLOT, status);
    }

    private BookingRequest request(BookingRequestStatus status, Booking... bookings) {
        List<Booking> occurrences = List.of(bookings);
        LocalDate last = occurrences.get(occurrences.size() - 1).date();
        StatusChange created = new StatusChange(null, null, BookingRequestStatus.PENDING, moderator.userId(),
                Instant.parse("2030-04-01T08:00:00Z"));
        return BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), ROOM, OWN, moderator.userId(),
                new Reason("Catechesis"), null, occurrences.get(0).date(), last, SLOT, status, occurrences,
                List.of(created));
    }

    private BookingRequest approvedTomorrow() {
        return request(BookingRequestStatus.APPROVED, booking(TODAY.plusDays(1), BookingStatus.APPROVED));
    }

    @Test
    void aModeratorCancelsAnApprovedRequestOfTheirCommunity() {
        BookingRequest request = approvedTomorrow();

        request.cancel(moderator, NOW, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.CANCELLED);
    }

    @Test
    void anAdministratorCancelsARequestOfAnyCommunity() {
        BookingRequest request = approvedTomorrow();

        request.cancel(administrator, NOW, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
    }

    @Test
    void aPendingRequestCanBeCancelled() {
        BookingRequest request = request(BookingRequestStatus.PENDING,
                booking(TODAY.plusDays(1), BookingStatus.PENDING));

        request.cancel(moderator, NOW, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.CANCELLED);
    }

    @Test
    void aModeratorOfAnotherCommunityCannotCancel() {
        BookingRequest request = approvedTomorrow();

        assertThatThrownBy(() -> request.cancel(otherModerator, NOW, CANCELLED_AT))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(request.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void aRegisteredUserCannotCancel() {
        BookingRequest request = approvedTomorrow();

        assertThatThrownBy(() -> request.cancel(registered, NOW, CANCELLED_AT)).isInstanceOf(SpacesForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(value = BookingRequestStatus.class, names = {"REJECTED", "CANCELLED"})
    void cancellingARejectedOrCancelledRequestFails(BookingRequestStatus status) {
        BookingRequest request = request(status, booking(TODAY.plusDays(1), BookingStatus.CANCELLED));

        assertThatThrownBy(() -> request.cancel(moderator, NOW, CANCELLED_AT))
                .isInstanceOf(BookingRequestNotActive.class);
        assertThatThrownBy(() -> request.cancelBooking(moderator, request.bookings().get(0).id(), NOW, CANCELLED_AT))
                .isInstanceOf(BookingRequestNotActive.class);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void cancellingRecordsWhoAndWhenInTheHistory() {
        BookingRequest request = approvedTomorrow();

        request.cancel(moderator, NOW, CANCELLED_AT);

        assertThat(request.history()).hasSize(2);
        assertThat(request.history().get(1)).isEqualTo(new StatusChange(null, BookingRequestStatus.APPROVED,
                BookingRequestStatus.CANCELLED, moderator.userId(), CANCELLED_AT));
    }

    @Test
    void cancellingASeriesKeepsPastAndStartedOccurrencesAndCancelsTheFutureOnes() {
        Booking past = booking(TODAY.minusDays(7), BookingStatus.APPROVED);
        Booking started = booking(TODAY, BookingStatus.APPROVED);
        Booking future = booking(TODAY.plusDays(7), BookingStatus.APPROVED);
        Booking later = booking(TODAY.plusDays(14), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, past, started, future, later);
        LocalDateTime afterStart = TODAY.atTime(18, 30);

        request.cancel(moderator, afterStart, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(request.bookings()).extracting(Booking::status).containsExactly(BookingStatus.APPROVED,
                BookingStatus.APPROVED, BookingStatus.CANCELLED, BookingStatus.CANCELLED);
    }

    @Test
    void cancellingASeriesDoesNotTouchOccurrencesThatWereAlreadyCancelled() {
        Booking cancelled = booking(TODAY.plusDays(1), BookingStatus.CANCELLED);
        Booking future = booking(TODAY.plusDays(8), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, cancelled, future);

        request.cancel(moderator, NOW, CANCELLED_AT);

        assertThat(request.bookings()).extracting(Booking::status)
                .containsExactly(BookingStatus.CANCELLED, BookingStatus.CANCELLED);
    }

    @Test
    void cancellingARequestWhoseOccurrencesAllStartedFails() {
        Booking past = booking(TODAY.minusDays(7), BookingStatus.APPROVED);
        Booking started = booking(TODAY, BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, past, started);

        assertThatThrownBy(() -> request.cancel(moderator, TODAY.atTime(18, 0), CANCELLED_AT))
                .isInstanceOf(BookingAlreadyStarted.class);
        assertThat(request.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.APPROVED);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void anOccurrenceStartingLaterTodayCanStillBeCancelled() {
        BookingRequest request = request(BookingRequestStatus.APPROVED, booking(TODAY, BookingStatus.APPROVED));

        request.cancel(moderator, TODAY.atTime(17, 59), CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
    }

    @Test
    void cancellingASingleOccurrenceLeavesTheOthersAndTheRequestActive() {
        Booking first = booking(TODAY.plusDays(1), BookingStatus.APPROVED);
        Booking second = booking(TODAY.plusDays(8), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, first, second);

        request.cancelBooking(moderator, second.id(), NOW, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(request.bookings()).extracting(Booking::status)
                .containsExactly(BookingStatus.APPROVED, BookingStatus.CANCELLED);
        assertThat(request.history()).hasSize(2);
        assertThat(request.history().get(1)).isEqualTo(new StatusChange(second.id(), BookingRequestStatus.APPROVED,
                BookingRequestStatus.CANCELLED, moderator.userId(), CANCELLED_AT));
    }

    @Test
    void cancellingTheLastActiveOccurrenceCancelsTheRequest() {
        Booking cancelled = booking(TODAY.plusDays(1), BookingStatus.CANCELLED);
        Booking last = booking(TODAY.plusDays(8), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, cancelled, last);

        request.cancelBooking(administrator, last.id(), NOW, CANCELLED_AT);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(request.history()).hasSize(3);
        assertThat(request.history().get(1).bookingId()).isEqualTo(last.id());
        assertThat(request.history().get(2)).isEqualTo(new StatusChange(null, BookingRequestStatus.APPROVED,
                BookingRequestStatus.CANCELLED, administrator.userId(), CANCELLED_AT));
    }

    @Test
    void cancellingAnOccurrenceThatStartedFails() {
        Booking started = booking(TODAY, BookingStatus.APPROVED);
        Booking future = booking(TODAY.plusDays(7), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, started, future);

        assertThatThrownBy(() -> request.cancelBooking(moderator, started.id(), TODAY.atTime(18, 0), CANCELLED_AT))
                .isInstanceOf(BookingAlreadyStarted.class);
        assertThat(request.bookings()).extracting(Booking::status).containsOnly(BookingStatus.APPROVED);
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void cancellingAnUnknownOccurrenceIsNotFound() {
        BookingRequest request = approvedTomorrow();

        assertThatThrownBy(() -> request.cancelBooking(moderator, new BookingId(UUID.randomUUID()), NOW,
                CANCELLED_AT)).isInstanceOf(BookingRequestNotFound.class);
    }

    @Test
    void cancellingAnAlreadyCancelledOccurrenceFails() {
        Booking cancelled = booking(TODAY.plusDays(1), BookingStatus.CANCELLED);
        Booking active = booking(TODAY.plusDays(8), BookingStatus.APPROVED);
        BookingRequest request = request(BookingRequestStatus.APPROVED, cancelled, active);

        assertThatThrownBy(() -> request.cancelBooking(moderator, cancelled.id(), NOW, CANCELLED_AT))
                .isInstanceOf(BookingRequestNotActive.class);
    }

    @Test
    void cancellingASingleOccurrenceRequiresAccessToTheCommunity() {
        BookingRequest request = approvedTomorrow();
        BookingId id = request.bookings().get(0).id();

        assertThatThrownBy(() -> request.cancelBooking(otherModerator, id, NOW, CANCELLED_AT))
                .isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> request.cancelBooking(registered, id, NOW, CANCELLED_AT))
                .isInstanceOf(SpacesForbidden.class);
    }
}

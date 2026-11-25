package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.spaces.application.service.AnonymizeRequesterService;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AnonymizeRequesterServiceTest {

    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final LocalDate DATE = LocalDate.of(2030, 5, 11);

    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();
    private final UserId deleted = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    private AnonymizeRequesterService service;

    @BeforeEach
    void setUp() {
        service = new AnonymizeRequesterService(requests);
    }

    private BookingRequest stored(UserId requester, UserId resolver) {
        Booking booking = new Booking(new BookingId(UUID.randomUUID()), DATE, SLOT, BookingStatus.APPROVED);
        List<StatusChange> history = List.of(
                new StatusChange(null, null, BookingRequestStatus.PENDING, requester, Instant.parse("2030-05-01T10:00:00Z")),
                new StatusChange(null, BookingRequestStatus.PENDING, BookingRequestStatus.APPROVED, resolver,
                        Instant.parse("2030-05-02T10:00:00Z")));
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()),
                new RoomId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()), requester, new Reason("Catechesis"),
                null, DATE, DATE, SLOT, BookingRequestStatus.APPROVED, List.of(booking), history));
    }

    @Test
    void clearsTheRequesterAndTheChangerOfTheDeletedUser() {
        BookingRequest request = stored(deleted, deleted);

        service.anonymize(deleted);

        BookingRequest found = requests.findById(request.id()).orElseThrow();
        assertThat(found.requestedBy()).isEmpty();
        assertThat(found.history()).extracting(StatusChange::changedBy).containsOnlyNulls();
    }

    @Test
    void keepsTheRequestItsOccurrencesAndItsReason() {
        BookingRequest request = stored(deleted, other);

        service.anonymize(deleted);

        BookingRequest found = requests.findById(request.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(found.reason()).isEqualTo(new Reason("Catechesis"));
        assertThat(found.bookings()).hasSize(1);
        assertThat(found.history()).hasSize(2);
    }

    @Test
    void clearsOnlyTheChangesOfTheDeletedUserWhenAnotherUserResolved() {
        BookingRequest request = stored(deleted, other);

        service.anonymize(deleted);

        assertThat(requests.findById(request.id()).orElseThrow().history()).extracting(StatusChange::changedBy)
                .containsExactly(null, other);
    }

    @Test
    void leavesTheRequestsOfOtherUsersUntouched() {
        BookingRequest request = stored(other, other);

        service.anonymize(deleted);

        BookingRequest found = requests.findById(request.id()).orElseThrow();
        assertThat(found.requestedBy()).contains(other);
        assertThat(found.history()).extracting(StatusChange::changedBy).containsExactly(other, other);
    }

    @Test
    void aUserWithoutRequestsChangesNothing() {
        service.anonymize(deleted);

        assertThat(requests.stored).isEmpty();
    }

    @Test
    void logsTheDeletedUserIdWhenSomethingIsAnonymized() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AnonymizeRequesterService.class);
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            stored(deleted, other);
            service.anonymize(deleted);
        } finally {
            serviceLogger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                .contains(tuple("userId", deleted.value()));
    }
}

package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.application.service.PurgeExpiredBookingsService;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
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

class PurgeExpiredBookingsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T22:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TODAY = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));

    private final InMemoryBookingRequests requests = new InMemoryBookingRequests();

    private PurgeExpiredBookingsService service;

    @BeforeEach
    void setUp() {
        service = new PurgeExpiredBookingsService(requests, CLOCK);
    }

    private BookingRequest endingOn(LocalDate lastDate) {
        Booking booking = new Booking(new BookingId(UUID.randomUUID()), lastDate, SLOT, BookingStatus.APPROVED);
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()),
                new RoomId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                new Reason("Catechesis"), null, lastDate, lastDate, SLOT, BookingRequestStatus.APPROVED,
                List.of(booking), List.of()));
    }

    @Test
    void deletesRequestsEndedMoreThanOneYearAgo() {
        BookingRequest expired = endingOn(TODAY.minusYears(1).minusDays(1));

        service.purge();

        assertThat(requests.stored).doesNotContainKey(expired.id());
    }

    @Test
    void keepsRequestsEndedExactlyOneYearAgo() {
        BookingRequest boundary = endingOn(TODAY.minusYears(1));

        service.purge();

        assertThat(requests.stored).containsKey(boundary.id());
    }

    @Test
    void keepsRecentAndFutureRequests() {
        BookingRequest recent = endingOn(TODAY.minusDays(3));
        BookingRequest future = endingOn(TODAY.plusDays(3));

        service.purge();

        assertThat(requests.stored).containsKeys(recent.id(), future.id());
    }

    @Test
    void usesTheMadridDateAroundMidnight() {
        BookingRequest boundary = endingOn(LocalDate.of(2029, 5, 11));
        BookingRequest expired = endingOn(LocalDate.of(2029, 5, 10));

        service.purge();

        assertThat(requests.stored).containsKey(boundary.id()).doesNotContainKey(expired.id());
    }
}

package io.github.escumartin.neocatecumenal.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.agenda.application.service.PurgeExpiredEventsService;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurgeExpiredEventsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T22:30:00Z"),
            ZoneId.of("Europe/Madrid"));
    private static final LocalDate TODAY = LocalDate.of(2030, 5, 11);

    private final InMemoryEvents events = new InMemoryEvents();

    private PurgeExpiredEventsService service;

    @BeforeEach
    void setUp() {
        service = new PurgeExpiredEventsService(events, CLOCK);
    }

    private Event on(LocalDate date) {
        return events.save(Event.restore(new EventId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()),
                EventType.WORD, new EventTitle("Opening word"), date,
                new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                Instant.parse("2030-05-01T10:00:00Z")));
    }

    @Test
    void deletesEventsDatedMoreThanOneYearAgo() {
        Event expired = on(TODAY.minusYears(1).minusDays(1));

        service.purge();

        assertThat(events.stored).doesNotContainKey(expired.id());
    }

    @Test
    void keepsEventsDatedExactlyOneYearAgo() {
        Event boundary = on(TODAY.minusYears(1));

        service.purge();

        assertThat(events.stored).containsKey(boundary.id());
    }

    @Test
    void keepsRecentAndFutureEvents() {
        Event recent = on(TODAY.minusDays(3));
        Event future = on(TODAY.plusDays(3));

        service.purge();

        assertThat(events.stored).containsKeys(recent.id(), future.id());
    }
}
